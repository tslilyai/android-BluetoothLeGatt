#include "SDDRRadio.h"

using namespace std;

uint64_t sddrStartTimestamp = getTimeMS();

SDDRRadio::ConfirmScheme SDDRRadio::getDefaultConfirmScheme()
{
  return {ConfirmScheme::Passive, 0.05};
}

SDDRRadio::SDDRRadio(size_t keySize, ConfirmScheme confirmScheme, MemoryScheme memoryScheme, bool retroactive, int adapterID, EbNHystPolicy hystPolicy, uint64_t rssiReportInterval)
   : nextDeviceID_(0),
     keySize_(keySize),
     confirmScheme_(confirmScheme),
     memoryScheme_(memoryScheme),
     allowRetroactiveLinking_(retroactive),
     advertisedSet_(),
     listenSet_(),
     setMutex_(),
     recentDevices_(),
     idToRecentDevices_(),
     nextDiscover_(getTimeMS() + 10000),
     nextChangeEpoch_(getTimeMS() + EPOCH_INTERVAL),
     RS_W(computeRSSymbolSize(keySize, (ADVERT_LEN*8) - 1 - ADV_N_LOG2)),
     RS_K((keySize / 8) / RS_W),
     RS_M(ADV_N - RS_K),
     BF_SM((ADVERT_LEN*8) - 1 - ADV_N_LOG2 - (RS_W*8)),
     BF_K(1),
     BF_B(2),
     deviceMap_(),
     dhCodeMatrix_(RS_K, RS_M + (2 * (RS_K - 1)), RS_W),
     dhEncoder_(dhCodeMatrix_),
     dhPrevSymbols_((2 * (RS_K - 1)) * RS_W),
     dhExchange_(keySize),
     dhExchangeMutex_(),
     advertNum_(0),
     advertBloom_(),
     advertBloomNum_(-1),
     hystPolicy_(hystPolicy),
     rssiReportInterval_(rssiReportInterval)
{
    LOG_D(TAG, "General Parameters: ADV_N = %zu, ADV_N_LOG2 = %zu", ADV_N, ADV_N_LOG2);
    LOG_D(TAG, "RS Parameters: W = %zu, K = %zu, M = %zu", RS_W, RS_K, RS_M);
    LOG_D(TAG, "BF Parameters: SM = %zu", BF_SM);

    // initialize the random number generator
    FILE *urand = fopen("/dev/urandom", "r");
    unsigned int seed = 0;
    for(int b = 0; b < sizeof(seed); b++)
    {
        seed <<= 8;
        seed |= (uint8_t)getc(urand);
    }
    srand(seed);
    dhEncoder_.encode(dhExchange_.getPublicX());
}

const Address SDDRRadio::getRandomAddr() {
    // get a new random address. uses the 1 bit Y coordinate as part of the
    // address since compressed ECDH keys are actually (keySize + 1) bits long
    uint8_t partial = (uint8_t)dhExchange_.getPublicY() << 5;
    Address addr = Address::newIDWithPartial(partial, 0x20);
    return addr;
}

char const* SDDRRadio::changeAndGetAdvert() {
    BitMap newAdvert;
    if(advertNum_ >= ADV_N)
    {
        LOG_D(TAG, "Reached last unique advert, waiting for epoch change\n");
        newAdvert = lastAdvert_;
    } else {
        newAdvert = generateAdvert(advertNum_);
        lastAdvert_ = newAdvert;
        LOG_D(TAG, "New Advert #%ld -- %s", advertNum_, newAdvert.toHexString().c_str());
        advertNum_++;
    }
    return (char*)newAdvert.toByteArray();
}

SDDRRadio::ActionInfo SDDRRadio::getNextAction()
{
  int64_t timeUntil = 0;
  const uint64_t curTime = getTimeMS();

  if(nextChangeEpoch_ < nextDiscover_)
  {
    if(nextChangeEpoch_ > curTime)
    {
      timeUntil = nextChangeEpoch_ - curTime;
    }
    return ActionInfo(Action::ChangeEpoch, timeUntil);
  }
  else
  {
    if(nextDiscover_ > curTime)
    {
      timeUntil = nextDiscover_ - curTime;
    }
    return ActionInfo(Action::Discover, timeUntil);
  }
}

void SDDRRadio::changeEpoch()
{
    advertNum_ = 0;

    lock_guard<mutex> dhExchangeLock(dhExchangeMutex_);

    // Generate a new secret for this epoch's DH exchanges
    dhExchange_.generateSecret();
    // Copying the leftover symbols from the prior epoch, which we will later
    // include in the first K-1 adverts of this epoch
    for(int k = 0; k < (RS_K - 1); k++)
    {
        memcpy(dhPrevSymbols_.data() + (k * RS_W), dhEncoder_.getSymbol(RS_K + RS_M + k), RS_W);
    }

    // Generating new symbols based on an updated DH local public value
    dhEncoder_.encode(dhExchange_.getPublicX());

    // Computing new shared secrets in the case of passive or hybrid confirmation
    if((confirmScheme_.type & ConfirmScheme::Passive) != 0)
    {
        for(auto it = deviceMap_.begin(); it != deviceMap_.end(); it++)
        {
            EbNDevice *device = it->second;
            if(!device->epochs_.empty())
            {
                LOG_D(TAG, "-- Computing shared secret for id %ld", device->getID());
                EbNDevice::Epoch &curEpoch = device->epochs_.back();
                if(curEpoch.dhDecoder.isDecoded())
                {
                    SharedSecret sharedSecret(confirmScheme_.type == ConfirmScheme::None);
                    if(dhExchange_.computeSharedSecret(sharedSecret, 
                        curEpoch.dhDecoder.decode(), curEpoch.dhExchangeYCoord))
                    {
                        LOG_D(TAG, "-- Adding shared secret %s for id %ld", 
                                sharedSecret.toString().c_str(),
                                device->getID());
                        device->addSharedSecret(sharedSecret);
                    }
                    else
                    {
                        LOG_D(TAG, "-- Could not compute shared secret for id %ld", device->getID());
                    }
                }
                else
                {
                    curEpoch.dhExchanges.push_back(dhExchange_);
                }
            }
        }
    }
    nextChangeEpoch_ += EPOCH_INTERVAL;
}


/* 
 * DISCOVERY FUNCTIONS 
 * */

void SDDRRadio::preDiscovery()
{
    if(memoryScheme_ == MemoryScheme::NoMemory)
        deviceMap_.clear();
    discovered_.clear();
}

void SDDRRadio::processScanResponse(Address addr, int8_t rssi, uint8_t* data)
{
    BitMap advert(ADVERT_LEN * 8, data);
    LOG_D(TAG, "Processing scan response with Addr %s, rssi %d, and data %s", addr.toString().c_str() , rssi, advert.toHexString().c_str());
    
    if (!addr.verifyChecksum()) {
        LOG_D(TAG, "Not an SDDR device, address checksum failed");
        return;
    }

    uint64_t scanTime = getTimeMS();
    EbNDevice *device = deviceMap_.get(addr);
    if(device == NULL)
    {
      lock_guard<mutex> setLock(setMutex_);

      device = new EbNDevice(generateDeviceID(), addr, listenSet_);
      deviceMap_.add(addr, device);

      LOG_D(TAG, "-- Discovered new SDDR device (ID %ld, Address %s)", 
              device->getID(), device->getAddress().toString().c_str());
    }

    device->addRSSIMeasurement(scanTime, rssi);
    discovered_.push_back(DiscoverEvent(scanTime, device->getID(), rssi));
    addRecentDevice(device);

    processAdvert(device, scanTime, data);
    processEpochs(device);
    LOG_D(TAG, "Processing adverts and epochs done");
}

vector<std::string> SDDRRadio::postDiscoveryGetEncounters()
{
    nextDiscover_ += SCAN_INTERVAL + (-1000 + (rand() % 2001));
    LOG_D(TAG, "-- Updated nextDiscover to %lld", nextDiscover_);
  
    // discovered_ is set from processScanResult
    // get the encounters from this discovery and store them away
    LOG_D(TAG, "-- discovered_ %d devices", discovered_.size());
    list<pair<DeviceID, uint64_t> > newlyDiscovered;
    set<DeviceID> toHandshake = hystPolicy_.discovered(discovered_, newlyDiscovered);
    hystPolicy_.encountered(handshake(toHandshake));

    list<EncounterEvent> encounters;

    for(auto ndIt = newlyDiscovered.begin(); ndIt != newlyDiscovered.end(); ndIt++)
    {
      EncounterEvent event(EncounterEvent::UnconfirmedStarted, ndIt->second, ndIt->first);
      encounters.push_back(event);
    }

    for(auto discIt = discovered_.begin(); discIt != discovered_.end(); discIt++)
    {
      EncounterEvent event(getTimeMS());
      if(getDeviceEvent(event, discIt->id, rssiReportInterval_))
      {
        encounters.push_back(event);
      }
    }

    list<pair<DeviceID, uint64_t> > expired = hystPolicy_.checkExpired();
    for(auto expIt = expired.begin(); expIt != expired.end(); expIt++)
    {
      EncounterEvent expireEvent = doneWithDevice(expIt->first);
      expireEvent.time = expIt->second;
      encounters.push_back(expireEvent);
    }

    std::vector<std::string> messages;
    for(auto encIt = encounters.begin(); encIt != encounters.end(); encIt++)
    {
        messages.push_back(encounterToMsg(*encIt)); 
    }
    LOG_D(TAG, "-- Sending %d encounters", messages.size());
    return messages;
}

/* 
 * ENCOUNTER / HANDSHAKE FUNCTIONS 
 */
std::string SDDRRadio::encounterToMsg(const EncounterEvent &event)
{
  LOG_D(TAG, "Encounter event took place");
  switch(event.type)
  {
  case EncounterEvent::UnconfirmedStarted:
    LOG_D(TAG, "Type = UnconfirmedStarted");
    break;
  case EncounterEvent::Started:
    LOG_D(TAG, "Type = Started");
    break;
  case EncounterEvent::Updated:
    LOG_D(TAG, "Type = Updated");
    break;
  case EncounterEvent::Ended:
    LOG_D(TAG, "Type = Ended");
    break;
  }
  LOG_D(TAG, "ID = %d", event.id);
  LOG_D(TAG, "Time = %" PRIu64, event.time);
  LOG_D(TAG, "# Shared Secrets = %d (Updated = %s)", 
          event.sharedSecrets.size(), event.sharedSecretsUpdated ? "true" : "false");
  LOG_D(TAG, "# Matching Set Entries = %d (Updated = %s)", 
          event.matching.size(), event.matchingSetUpdated ? "true" : "false");
  LOG_D(TAG, "# Bloom Filters = %d (Updated = %s)", 
          event.blooms.size(), event.bloomsUpdated? "true" : "false");
   LOG_D(TAG, "# RSSI Entries = %d", event.rssiEvents.size());

  SDDR::Event_EncounterEvent *encounterEvent = new SDDR::Event_EncounterEvent();
  encounterEvent->set_type((SDDR::Event_EncounterEvent_EventType)event.type);
  encounterEvent->set_time(event.time);
  encounterEvent->set_id(event.id);
  encounterEvent->set_pkid(event.getPKID());
  encounterEvent->set_address(event.address);
  encounterEvent->set_matchingsetupdated(event.matchingSetUpdated);

  for(auto it = event.rssiEvents.begin(); it != event.rssiEvents.end(); it++)
  {
    SDDR::Event_EncounterEvent_RSSIEvent *rssiEvent = encounterEvent->add_rssievents();
    rssiEvent->set_time(it->time);
    rssiEvent->set_rssi(it->rssi);
  }

  for(auto it = event.matching.begin(); it != event.matching.end(); it++)
  {
    LOG_D(TAG, "ADDING MATCHING ENTRY TO EVENT");
    encounterEvent->add_matchingset(it->get(), it->size());
  }

  for(auto it = event.sharedSecrets.begin(); it != event.sharedSecrets.end(); it++)
  {
    encounterEvent->add_sharedsecrets(it->value.get(), it->value.size());
  }

  SDDR::Event_RetroactiveInfo* info = new SDDR::Event_RetroactiveInfo();
  LOG_D(TAG, "BLOOM: Adding %d blooms for pkid %lld", event.blooms.size(), event.getPKID());
  for(auto it = event.blooms.begin(); it != event.blooms.end(); it++)
  {
    LOG_D(TAG, "BLOOM: Adding for %lld: prefix %s, prefixSize %d, bloom %s", event.getPKID(), it->prefix.toString().c_str(), it->prefix.sizeBytes(), it->bloom.toString().c_str());
    SDDR::Event_RetroactiveInfo_BloomInfo *binfo = info->add_blooms();
    binfo->set_prefix_bytes((const char*)it->prefix.toByteArray());
    binfo->set_prefix_size(it->prefix.sizeBytes());
    binfo->set_pfalse(it->pFalseDelta);
    // add the bloom filter itself
    SDDR::Event_RetroactiveInfo_BloomInfo_Bloom* bloom = binfo->mutable_bloom();
    {
        bloom->set_n_(it->bloom.N());
        bloom->set_k_(it->bloom.K());
        bloom->set_m_(it->bloom.M());
        bloom->set_bits_((const char*)it->bloom.toByteArray());
    }
  }

  std::string str;
  SDDR::Event fullEvent;
  fullEvent.set_allocated_encounterevent(encounterEvent);
  fullEvent.set_allocated_retroactiveinfo(info);
  fullEvent.SerializeToString(&str);
  return str;
}

std::set<DeviceID> SDDRRadio::handshake(const std::set<DeviceID> &deviceIDs)
{
    set<DeviceID> encountered;

    // ignore everything to do with active handshakes for now
    for(auto it = deviceIDs.begin(); it != deviceIDs.end(); it++)
    {
        EbNDevice *device = deviceMap_.get(*it);
        device->setShakenHands(true);
    }

    // Going through all devices to report 'encountered' devices, meaning
    // the devices we have shaken hands with and confirmed
    for(auto it = deviceMap_.begin(); it != deviceMap_.end(); it++)
    {
        EbNDevice *device = it->second;
        if(device->hasShakenHands() && device->isConfirmed())
        {
          encountered.insert(device->getID());
        }
    }

    LOG_D(TAG, "Ending handshake with %d encounters", encountered.size());
    return encountered;
}

EncounterEvent SDDRRadio::doneWithDevice(DeviceID id)
{
  EbNDevice *device = deviceMap_.get(id);

  EncounterEvent expiredEvent(getTimeMS());
  device->getEncounterInfo(expiredEvent, true);

  deviceMap_.remove(id);
  removeRecentDevice(id);

  LOG_D(TAG, "Done with device %d", id);
  return expiredEvent;
}

bool SDDRRadio::getRetroactiveMatches(LinkValueList& matching, std::list<BloomInfo>& blooms)
{
    matching = listenSet_;
    float matchingPFalse = 1;
    LOG_D(TAG, "BLOOM: Retroactive querying %d blooms", blooms.size());
    for (BloomInfo bi : blooms) {
        LinkValueList::iterator it = matching.begin();
        while(it != matching.end())
        {
            const LinkValue &value = *it;
            if(!bi.bloom.query(bi.prefix.toByteArray(), bi.prefix.sizeBytes(), value.get(), value.size()))
            {
                LOG_D(TAG, "---BLOOM: Retroactive didn't find %s", 
                        BitMap(value.size(), value.get()).toString().c_str());
                it = matching.erase(it);
            }
            else
            {
                LOG_D(TAG, "---BLOOM: Retroactive found %s", 
                        BitMap(value.size(), value.get()).toString().c_str())
                it++;
            }
        }
        matchingPFalse *= bi.pFalseDelta;
    }
    LOG_D(TAG, "BLOOM: Retroactive matching set is %d entries (pFalse %g)", matching.size(), matchingPFalse);
    // false if not significant enough for us to update matching
    return (matchingPFalse < 0.05);
}

/* 
 * ADVERT PROCESSING AND GENERATION FUNCTIONS
 */
BitMap SDDRRadio::generateAdvert(size_t advertNum)
{
  BitMap advert(25 * 8);
  size_t advertOffset = 0;

  advert.setAll(false);

  // NOTE: Version bit is 0, since we are not an infrastructure node
  advertOffset += 1;

  // Inserting the advertisement number as the first portion of the advert
  for(int b = 0; b < ADV_N_LOG2; b++)
  {
    advert.set(b + advertOffset, (advertNum >> b) & 0x1);
  }
  advertOffset += ADV_N_LOG2;

  // Insert the RS coded symbols for the DH public value. For the first K-1
  // adverts of an epoch, add in a symbol from the previous epoch. This allows a
  // user to decode if they receive any K consecutive packets
  advert.copyFrom(dhEncoder_.getSymbol(advertNum), 0, advertOffset, 8 * RS_W);
  advertOffset += 8 * RS_W;

  if(advertNum < (RS_K - 1))
  {
    advert.copyFrom(dhPrevSymbols_.data() + (advertNum * RS_W), 0, advertOffset, 8 * RS_W);
    advertOffset += 8 * RS_W;
  }

  // Computing a new Bloom filter every BF_B advertisements
  uint32_t bloomNum = advertNum / BF_B;

  if(((advertNum % BF_B) == 0) || (bloomNum != advertBloomNum_))
  {
    BitMap prefix(ADV_N_LOG2 + keySize_);
    for(int b = 0; b < ADV_N_LOG2; b++)
    {
      prefix.set(b, (bloomNum >> b) & 0x1);
    }
    prefix.copyFrom(dhExchange_.getPublicX(), 0, ADV_N_LOG2, keySize_);

    uint32_t totalSize = 0;
    vector<uint32_t> segmentSizes;
    for(int s = bloomNum * BF_B; s < (bloomNum + 1) * BF_B; s++)
    {
      uint32_t segmentSize = (s < (RS_K - 1)) ? (BF_SM - (8 * RS_W)) : BF_SM;
      segmentSizes.push_back(segmentSize);
      totalSize += segmentSize;
    }

    advertBloom_ = SegmentedBloomFilter(BF_N, totalSize, BF_K, BF_B, segmentSizes);
    fillBloomFilter(&advertBloom_, prefix.toByteArray(), prefix.sizeBytes());
    advertBloomNum_ = bloomNum;
  }
  advertBloom_.getSegment(advertNum % BF_B, advert.toByteArray(), advertOffset);
   
  return advert;
}

bool SDDRRadio::processAdvert(EbNDevice *device, uint64_t time, const uint8_t *data)
{
  BitMap advert(ADVERT_LEN * 8, data);
  size_t advertOffset = 0;

  // NOTE: Ignoring the version bit for now
  advertOffset += 1;

  uint32_t advertNum = 0;
  for(int b = 0; b < ADV_N_LOG2; b++)
  {
    if(advert.get(b + advertOffset))
    {
      advertNum |= (1 << b);
    }
  }
  advertOffset += ADV_N_LOG2;

  LOG_D(TAG, "Processing advert %u from device %d - '%s'", advertNum, device->getID(), advert.toHexString().c_str());

  if(advertNum < (RS_K + RS_M))
  {
    LOG_D(TAG, "-- advertnum < RS_K + RS_M");
    EbNDevice::Epoch *curEpoch = NULL;
    EbNDevice::Epoch *prevEpoch = NULL;
    bool isDuplicate = false;
    bool isNew = true;

    if(!device->epochs_.empty())
    {
      curEpoch = &device->epochs_.back();
      prevEpoch = &device->epochs_.front();
      if(prevEpoch == curEpoch)
      {
        prevEpoch = NULL;
      }

      uint64_t diffAdvertTime = time - curEpoch->lastAdvertTime;
      uint32_t diffAdvertNum = advertNum - curEpoch->lastAdvertNum;

      // This is a duplicate advertisement
      // We adjust the comparison since we have to check
      // against either the advertisement or scan response as last received
      if((curEpoch->lastAdvertNum == advertNum) || (curEpoch->lastAdvertNum == (advertNum + 1)))
      {
        if(diffAdvertTime < ((ADV_N / 2) * SCAN_INTERVAL))
        {
          isDuplicate = true;
          isNew = false;
        }
      }
      // This advertisement belongs to the current epoch
      else if((curEpoch->lastAdvertNum < advertNum) && (diffAdvertTime < (((diffAdvertNum + 63) * SCAN_INTERVAL) / 2)))
      {
        isNew = false;
      }
    }

    // Skip processing any advertisement that we have already seen
    if(!isDuplicate)
    {
      if(isNew)
      {
        prevEpoch = curEpoch;
        auto publicY = (device->getAddress().getPartialValue(0x20) >> 5) & 0x1;
        device->epochs_.push_back(EbNDevice::Epoch(advertNum, time, dhCodeMatrix_, dhExchange_, publicY));
        curEpoch = &device->epochs_.back();

        LOG_D(TAG, "-- Creating new epoch, previous epoch %s", (prevEpoch == NULL) ? "does not exist" : "exists");
      }

      // Processing the DH symbol for the current epoch
      uint8_t dhSymbol[RS_W];
      advert.copyTo(dhSymbol, 0, advertOffset, 8 * RS_W);
      curEpoch->dhDecoder.setSymbol(advertNum, dhSymbol);
      advertOffset += 8 * RS_W;

      // Processing the DH symbol for the previous epoch if we are within the
      // first K-1 symbols in the epoch, and the previous epoch exists in a
      // non-decoded state
      if(advertNum < (RS_K - 1))
      {
        if((prevEpoch != NULL) && !prevEpoch->dhDecoder.isDecoded())
        {
          advert.copyTo(dhSymbol, 0, advertOffset, 8 * RS_W);
          prevEpoch->dhDecoder.setSymbol(advertNum + RS_K + RS_M, dhSymbol);
        }

        advertOffset += 8 * RS_W;
      }

      // Looking up the segmented Bloom filter (or creating a new one) to hold the
      // segment from this advertisement
      uint32_t bloomNum = advertNum / BF_B;
      SegmentedBloomFilter *bloom;

      if(!curEpoch->blooms.empty() && (curEpoch->blooms.back().first == bloomNum))
      {
        bloom = &curEpoch->blooms.back().second;
      }
      else
      {
        uint32_t totalSize = 0;
        vector<uint32_t> segmentSizes;
        for(int s = bloomNum * BF_B; s < (bloomNum + 1) * BF_B; s++)
        {
          uint32_t segmentSize = (s < (RS_K - 1)) ? (BF_SM - (8 * RS_W)) : BF_SM;
          segmentSizes.push_back(segmentSize);
          totalSize += segmentSize;
        }

        curEpoch->blooms.push_back(make_pair(bloomNum, SegmentedBloomFilter(BF_N, totalSize, BF_K, BF_B, segmentSizes, true)));
        bloom = &curEpoch->blooms.back().second;
      }

      // Copying the segment over into the Bloom filter
      bloom->setSegment(advertNum % BF_B, advert.toByteArray(), advertOffset);

      LOG_D(TAG, "-- new bloom filter segment");
      return true;
    }
  }

  return false;
}

void SDDRRadio::processEpochs(EbNDevice *device)
{
  auto epochIt = device->epochs_.begin();
  while(epochIt != device->epochs_.end())
  {
    EbNDevice::Epoch &epoch = *epochIt;

    // Decoding the DH remote public value when possible
    if(!epoch.dhDecoder.isDecoded() && epoch.dhDecoder.canDecode())
    {
        LOG_D(TAG, "-- Can decode %d", device->getID());
      const uint8_t *dhRemotePublic = epoch.dhDecoder.decode();
      epoch.decodeBloomNum = epoch.blooms.back().first;

      // Computing shared secret(s) from the DH exchange(s), only for non-active confirmation schemes
      if((confirmScheme_.type & ConfirmScheme::Active) != ConfirmScheme::Active)
      {
        for(auto dhIt = epoch.dhExchanges.begin(); dhIt != epoch.dhExchanges.end();  dhIt++)
        {
          ECDH &dhExchange = *dhIt;
          SharedSecret sharedSecret(confirmScheme_.type == ConfirmScheme::None);
          if(dhExchange.computeSharedSecret(sharedSecret, dhRemotePublic, epoch.dhExchangeYCoord))
          {
            LOG_D(TAG, "-- Adding shared secret %s for device id %d", 
                    sharedSecret.toString().c_str(), device->getID());
            device->addSharedSecret(sharedSecret);
          }
          else
          {
            LOG_D(TAG, "-- Could not compute shared secret for id %d", device->getID());
          }
        }
      }
    }

    // Processing all of the Bloom filters
    // TODO: Should store hashes on a per Bloom filter basis, so that there is
    // no overhead in checking individual segments as they come in
    if(epoch.dhDecoder.isDecoded() && !epoch.blooms.empty())
    {
      auto bloomIt = epoch.blooms.begin();
      while(bloomIt != epoch.blooms.end())
      {
        uint32_t bloomNum = bloomIt->first;
        SegmentedBloomFilter &bloom = bloomIt->second;

        // Only processing the Bloom filter if a new segment was added
        if(bloom.pFalse() != 1)
        {
          BitMap prefix(ADV_N_LOG2 + keySize_);
          for(int b = 0; b < ADV_N_LOG2; b++)
          {
            prefix.set(b, (bloomNum >> b) & 0x1);
          }
          prefix.copyFrom(epoch.dhDecoder.decode(), 0, ADV_N_LOG2, keySize_);

          // Updating the matching set, as well as shared secret confidence in the
          // case of passive or hybrid confirmation
          float bloomPFalse = bloom.resetPFalse();

          if (allowRetroactiveLinking_) {
              device->updateBlooms(&bloom, &prefix, bloomPFalse);
          }
          device->updateMatching(&bloom, prefix.toByteArray(), prefix.sizeBytes(), bloomPFalse);
          if(((confirmScheme_.type & ConfirmScheme::Passive) != 0) && (bloomNum > epoch.decodeBloomNum))
          {
            device->confirmPassive(&bloom, prefix.toByteArray(), prefix.sizeBytes(), confirmScheme_.threshold, bloomPFalse);
          }
        }

        // Removing any Bloom filters we are finished with (not the latest one,
        // unless the last segment is filled)
        if((bloomNum != epoch.blooms.back().first) || bloom.isFilled(BF_B - 1))
        {
           bloomIt = epoch.blooms.erase(bloomIt);
        }
        else
        {
          bloomIt++;
        }
      }
    }

    // Removing any past epochs that we are finished with
    bool removed = false;
    if(distance(epochIt, device->epochs_.end()) > 1)
    {
      bool isDecoded = epoch.dhDecoder.isDecoded();
      bool isBeforePrevious = (device->epochs_.size() > 2);
      if(isDecoded || isBeforePrevious)
      {
        epochIt = device->epochs_.erase(epochIt);
        removed = true;

        LOG_D(TAG, "Finished with a prior epoch [Decoded? %d] [Before Previous? %d]", isDecoded, isBeforePrevious);
      }
    }

    if(!removed)
    {
      epochIt++;
    }
  }
}

/* 
 * UTIL FUNCTIONS
 */
size_t SDDRRadio::computeRSSymbolSize(size_t keySize, size_t advertBits)
{
  size_t bestW = 0;
  size_t bestNumAdverts = 100000;
  for(size_t w = 1; w < (keySize / 8); w++)
  {
    if((keySize / 8) % w != 0)
    {
      continue;
    }

    // This assumes knowledge of using BF_B = 2, BF_K = 1, and that the
    // threshold for passive confirmation is 95%.
    // TODO: Fix this ^
    size_t bloomBits = 2 * (advertBits - (8 * w));
    float bloomPFalse = BloomFilter::computePFalse(BF_N, bloomBits, 1);

    size_t advertsDecode = (keySize / 8) / w;
    size_t advertsConfirm = ceil(2 * (log(0.05) / log(bloomPFalse)));
    size_t advertsTotal = advertsDecode + advertsConfirm;

    if(w < ADVERT_LEN && 
            ((advertsTotal < bestNumAdverts) 
             || ((advertsTotal == bestNumAdverts) && (w < bestW))))
    {
      bestW = w;
      bestNumAdverts = advertsTotal;

      LOG_D(TAG, "Compute RS Symbol Size - New Best (W = %zu, #Advert = %zu)", bestW, bestNumAdverts);
    }
  }

    LOG_D(TAG, "Done RSSymbolSize (W = %zu)", bestW);
    return bestW;
}

const char *SDDRRadio::memorySchemeStrings[] = { "Standard", "No Memory" };
const char *SDDRRadio::confirmSchemeStrings[] = { "None", "Passive", "Active", "Hybrid" };
SDDRRadio::ConfirmScheme::Type SDDRRadio::stringToConfirmScheme(const char* name)
{
  ConfirmScheme::Type type = ConfirmScheme::END;

  for(int t = 0; t < SDDRRadio::ConfirmScheme::END; t++)
  {
    if(strcmp(name, SDDRRadio::confirmSchemeStrings[t]) == 0)
    {
      type = (ConfirmScheme::Type)t;
    }
  }

  return type;
}

void SDDRRadio::setAdvertisedSet(const LinkValueList &advertisedSet)
{
  lock_guard<mutex> setLock(setMutex_);
  advertisedSet_ = advertisedSet;
}

void SDDRRadio::setListenSet(const LinkValueList &listenSet)
{
  lock_guard<mutex> setLock(setMutex_);
  listenSet_ = listenSet;
}

void SDDRRadio::fillBloomFilter(BloomFilter *bloom, const uint8_t *prefix, uint32_t prefixSize, bool includePassive)
{
  lock_guard<mutex> setLock(setMutex_);
  fillBloomFilter(bloom, advertisedSet_, prefix, prefixSize, includePassive);
}

void SDDRRadio::fillBloomFilter(BloomFilter *bloom, const LinkValueList &advertisedSet, const uint8_t *prefix, uint32_t prefixSize, bool includePassive)
{
  int numRandom = 0;

  // Inserting link values from the advertised set
  int maxAdvert = BF_N;
  if((confirmScheme_.type & ConfirmScheme::Passive) != 0)
  {
    maxAdvert = BF_N - BF_N_PASSIVE;
  }

  int numAdvert = 0;
  for(auto it = advertisedSet.cbegin(); (it != advertisedSet.cend()) && (numAdvert < maxAdvert);  it++, numAdvert++)
  {
    bloom->add(prefix, prefixSize, it->get(), it->size());
  }

  numRandom += (maxAdvert - numAdvert);

  // Inserting link values for passive confirmation, using the most confident
  // shared secrets from all recently discovered devices. This corresponds to
  // the "Highest Confidence (HC)" scheme mentioned in the paper.
  int numPassive = 0;
  if(includePassive && ((confirmScheme_.type & ConfirmScheme::Passive) != 0))
  {
    SharedSecretQueue secrets;

    for(auto devIt = recentDevices_.begin(); devIt != recentDevices_.end(); devIt++)
    {
      SharedSecretList deviceSecrets = (*devIt)->getSharedSecrets();
      for(auto secIt = deviceSecrets.begin(); secIt != deviceSecrets.end(); secIt++)
      {
        // Only include secrets which were not actively confirmed
        if(secIt->confirmedBy != SharedSecret::ConfirmScheme::Active)
        {
          secrets.push(*secIt);
        }
      }
    }

    while(!secrets.empty() && (numPassive < BF_N_PASSIVE))
    {
      const SharedSecret &secret = secrets.top();
      bloom->add(prefix, prefixSize, secret.value.get(), secret.value.size());
      secrets.pop();
      numPassive++;
    }

    numRandom += (BF_N_PASSIVE - numPassive);
  }

  // Inserting random link values to ensure constant Bloom filter load
  bloom->addRandom(numRandom);

  LOG_D("SDDRRadio", "Created new Bloom filter (Advertised %d, Passive %d, Random %d)", 
          numAdvert, numPassive, numRandom);
}

void SDDRRadio::addRecentDevice(EbNDevice *device)
{
  removeRecentDevice(device->getID());

  recentDevices_.push_front(device);
  idToRecentDevices_.insert(make_pair(device->getID(), recentDevices_.begin()));
  LOG_D(TAG, "Recent device %d", device->getID());
}

void SDDRRadio::removeRecentDevice(DeviceID id)
{
  IDToRecentDeviceMap::iterator it = idToRecentDevices_.find(id);
  if(it != idToRecentDevices_.end())
  {
    recentDevices_.erase(it->second);
    idToRecentDevices_.erase(it);
  }
}

bool SDDRRadio::getDeviceEvent(EncounterEvent &event, DeviceID id, uint64_t rssiReportInterval)
{
  IDToRecentDeviceMap::iterator it = idToRecentDevices_.find(id);
  if(it != idToRecentDevices_.end())
  {
    EbNDevice *device = *it->second;
    if(device->getEncounterInfo(event, rssiReportInterval))
    {
      return true;
    }
  }

  return false;
}
