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
     timeDetectedNewDevice_(),
     timeDetectedUnconfirmedDevice_(),
     deviceMap_(),
     dhExchange_(keySize),
     dhExchangeMutex_(),
     advertNum_(0),
     advertBloom_(),
     advertBloomNum_(-1),
     hystPolicy_(hystPolicy),
     rssiReportInterval_(rssiReportInterval)
{
    // initialize the random number generator
    FILE *urand = fopen("/dev/urandom", "r");
    unsigned int seed = 0;
    for(int b = 0; b < sizeof(seed); b++)
    {
        seed <<= 8;
        seed |= (uint8_t)getc(urand);
    }
    srand(seed);
	advert_ = generateAdvert(dhExchange);
}

const Address SDDRRadio::getRandomAddr() {
    // get a new random address. uses the 1 bit Y coordinate as part of the
    // address since compressed ECDH keys are actually (keySize + 1) bits long
    uint8_t partial = (uint8_t)dhExchange_.getPublicY() << 5;
    Address addr = Address::newIDWithPartial(partial, 0x20);
    return addr;
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
    
    // Computing new shared secrets in the case of passive or hybrid confirmation
    // TODO ES?
    /*LOG_D("ENCOUNTERS_TEST", "-- Adding shared secret %s for id %ld", 
            sharedSecret.toString().c_str(),
            device->getID());
    device->addSharedSecret(sharedSecret);*/
    nextChangeEpoch_ += EPOCH_INTERVAL;
    advert_ = generateAdvert(dhExchange_);
}


/* 
 * DISCOVERY FUNCTIONS 
 * */

void SDDRRadio::preDiscovery()
{
    discovered_.clear();
}

bool SDDRRadio::processScanResponse(Address addr, int8_t rssi, uint8_t* data)
{
    BitMap advert(ADVERT_LEN * 8, data);
    bool newlyFound = false;
    LOG_P(TAG, "Processing scan response with Addr %s, rssi %d, and data %s", addr.toString().c_str() , rssi, advert.toHexString().c_str());
    
    if (!addr.verifyChecksum()) {
        LOG_P(TAG, "Not an SDDR device, address checksum failed");
        return false;
    }

    uint64_t scanTime = getTimeMS();
    EbNDevice *device = deviceMap_.get(addr);
    if(device == NULL)
    {
      lock_guard<mutex> setLock(setMutex_);

      device = new EbNDevice(generateDeviceID(), addr, listenSet_);
      deviceMap_.add(addr, device);
      newlyFound = true;

      LOG_D("ENCOUNTERS_TEST", "-- Discovered new SDDR device (ID %ld, Address %s)", 
              device->getID(), device->getAddress().toString().c_str());
    }

    device->addRSSIMeasurement(scanTime, rssi);
    discovered_.push_back(DiscoverEvent(scanTime, device->getID(), rssi));
    LOG_P(TAG, "-- Discovered device %d", device->getID());
    addRecentDevice(device);

    processAdvert(device, scanTime, data);
    processEpochs(device);
    return newlyFound;
}

std::vector<std::string> SDDRRadio::postDiscoveryGetEncounters()
{
    // discovered_ is set from processScanResult
    // get the encounters from this discovery and store them away
    LOG_P(TAG, "-- discovered_ %d devices", discovered_.size());
    list<pair<DeviceID, uint64_t> > newlyDiscovered;
    set<DeviceID> toHandshake = hystPolicy_.discovered(discovered_, newlyDiscovered);
    hystPolicy_.encountered(handshake(toHandshake));

    list<EncounterEvent> encounters;

    for(auto ndIt = newlyDiscovered.begin(); ndIt != newlyDiscovered.end(); ndIt++)
    {
      EncounterEvent event(EncounterEvent::UnconfirmedStarted, ndIt->second, ndIt->first);
      encounters.push_back(event);
      timeDetectedNewDevice_ = event.time;
    }

    for(auto discIt = discovered_.begin(); discIt != discovered_.end(); discIt++)
    {
      EncounterEvent event(getTimeMS());
      if(getDeviceEvent(event, discIt->id, rssiReportInterval_))
      {
        encounters.push_back(event);
      } 
      else 
      {
          // the event wasn't updated! this means it wasn't confirmed yet
          timeDetectedUnconfirmedDevice_ = event.time;
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

    // stay in encounter-forming mode if the last time we saw a 
    // new device or unconfirmed device was less 5 minutes ago
    const uint64_t curTime = getTimeMS();
    bool SCAN_ENCOUNTERS_DETECTED = 
        (curTime - timeDetectedNewDevice_ < TIME_IDLE_MODE) 
        || (curTime - timeDetectedUnconfirmedDevice_ < TIME_IDLE_MODE);
    LOG_P(TAG, "Detected scan encounters? %d", SCAN_ENCOUNTERS_DETECTED);
    nextDiscover_ += SCAN_ENCOUNTERS_DETECTED 
        ? SCAN_INTERVAL_ENCOUNTERS + (-1000 + (rand() % 2001))
        : SCAN_INTERVAL_IDLE + (-1000 + (rand() % 2001));
    LOG_P(TAG, "-- Updated nextDiscover to %lld", nextDiscover_);
 
    LOG_P(TAG, "-- Sending %d encounters", messages.size());
    return messages;
}

/* 
 * ENCOUNTER / HANDSHAKE FUNCTIONS 
 */
std::string SDDRRadio::encounterToMsg(const EncounterEvent &event)
{
  LOG_P(TAG, "Encounter event took place");
  switch(event.type)
  {
  case EncounterEvent::UnconfirmedStarted:
    LOG_P(TAG, "Type = UnconfirmedStarted");
    break;
  case EncounterEvent::Started:
    LOG_P(TAG, "Type = Started");
    break;
  case EncounterEvent::Updated:
    LOG_P(TAG, "Type = Updated");
    break;
  case EncounterEvent::Ended:
    LOG_P(TAG, "Type = Ended");
    break;
  }
  LOG_P(TAG, "ID = %d", event.id);
  LOG_P(TAG, "Time = %" PRIu64, event.time);
  LOG_P(TAG, "# Shared Secrets = %d (Updated = %s)", 
          event.sharedSecrets.size(), event.sharedSecretsUpdated ? "true" : "false");
  LOG_P(TAG, "# Matching Set Entries = %d (Updated = %s)", 
          event.matching.size(), event.matchingSetUpdated ? "true" : "false");
  LOG_P(TAG, "# Bloom Filters = %d (Updated = %s)", 
          event.blooms.size(), event.bloomsUpdated? "true" : "false");
   LOG_P(TAG, "# RSSI Entries = %d", event.rssiEvents.size());

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
    LOG_P(TAG, "ADDING MATCHING ENTRY TO EVENT");
    encounterEvent->add_matchingset(it->get(), it->size());
  }

  for(auto it = event.sharedSecrets.begin(); it != event.sharedSecrets.end(); it++)
  {
    encounterEvent->add_sharedsecrets(it->value.get(), it->value.size());
  }

  SDDR::Event_RetroactiveInfo* info = new SDDR::Event_RetroactiveInfo();
  LOG_P(TAG, "BLOOM: Adding %d blooms for pkid %lld", event.blooms.size(), event.getPKID());
  for(auto it = event.blooms.begin(); it != event.blooms.end(); it++)
  {
    LOG_P(TAG, "BLOOM: Adding for %lld: prefix %s, prefixSize %d, bloom %s", event.getPKID(), it->prefix.toString().c_str(), it->prefix.sizeBytes(), it->bloom.toString().c_str());
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

    LOG_P(TAG, "Ending handshake with %d encounters", encountered.size());
    return encountered;
}

EncounterEvent SDDRRadio::doneWithDevice(DeviceID id)
{
  EbNDevice *device = deviceMap_.get(id);

  EncounterEvent expiredEvent(getTimeMS());
  device->getEncounterInfo(expiredEvent, true);

  deviceMap_.remove(id);
  removeRecentDevice(id);

  LOG_P(TAG, "Done with device %d", id);
  return expiredEvent;
}

bool SDDRRadio::getRetroactiveMatches(LinkValueList& matching, std::list<BloomInfo>& blooms)
{
    matching = listenSet_;
    float matchingPFalse = 1;
    LOG_P(TAG, "BLOOM: Retroactive querying %d blooms", blooms.size());
    for (BloomInfo bi : blooms) {
        LinkValueList::iterator it = matching.begin();
        while(it != matching.end())
        {
            const LinkValue &value = *it;
            if(!bi.bloom.query(bi.prefix.toByteArray(), bi.prefix.sizeBytes(), value.get(), value.size()))
            {
                LOG_P(TAG, "---BLOOM: Retroactive didn't find %s", 
                        BitMap(value.size(), value.get()).toString().c_str());
                it = matching.erase(it);
            }
            else
            {
                LOG_P(TAG, "---BLOOM: Retroactive found %s", 
                        BitMap(value.size(), value.get()).toString().c_str())
                it++;
            }
        }
        matchingPFalse *= bi.pFalseDelta;
    }
    LOG_P(TAG, "BLOOM: Retroactive matching set is %d entries (pFalse %g)", matching.size(), matchingPFalse);
    // false if not significant enough for us to update matching
    return (matchingPFalse < 0.05);
}

/* 
 * ADVERT PROCESSING AND GENERATION FUNCTIONS
 */
char const* SDDRRadio::generateAdvert(const ECDH &dhExchange)
{
  	size_t messageOffset = 0;
  	size_t messageSize = dhExchange.getPublicSize();
  	vector<uint8_t> message(messageSize, 0);

  	// Adding the full public key (X and Y coordinates) to the message
  	memcpy(message.data(), dhExchange.getPublic(), dhExchange.getPublicSize());
	const char str[] = dhExchange_.getPublicX();
  	unsigned char hash[SHA_DIGEST_LENGTH]; // == 20
  	SHA1(message.data(), messageSize, hash);
	return hash;
}

bool SDDRRadio::processAdvert(EbNDevice *device, uint64_t time, const uint8_t *data)
{
	std::string advert = std::string(data, ADVERT_LEN); 
	LOG_P(TAG, "Processing advert %u from device %d - '%s'", advertNum, device->getID(), advert.toHexString().c_str());

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

	  if (curEpoch->advert = advert)
      uint64_t diffAdvertTime = time - curEpoch->lastAdvertTime;
      uint32_t diffAdvertNum = advertNum - curEpoch->lastAdvertNum;

      // This is a duplicate advertisement
      // We adjust the comparison since we have to check
      // against either the advertisement or scan response as last received
      if((curEpoch->lastAdvertNum == advertNum) || (curEpoch->lastAdvertNum == (advertNum + 1)))
      {
        if(diffAdvertTime < ((ADV_N / 2) * SCAN_INTERVAL_ENCOUNTERS))
        {
          isDuplicate = true;
          isNew = false;
        }
      }
      // This advertisement belongs to the current epoch
      else if((curEpoch->lastAdvertNum < advertNum) && (diffAdvertTime < (((diffAdvertNum + 63) * SCAN_INTERVAL_ENCOUNTERS) / 2)))
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

        LOG_P(TAG, "-- Creating new epoch, previous epoch %s", (prevEpoch == NULL) ? "does not exist" : "exists");
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

      LOG_P(TAG, "-- new bloom filter segment");
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
        LOG_P(TAG, "-- Can decode %d", device->getID());
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
            LOG_D("ENCOUNTERS_TEST", "-- Adding shared secret %s for device id %d", 
                    sharedSecret.toString().c_str(), device->getID());
            device->addSharedSecret(sharedSecret);
          }
          else
          {
            LOG_P(TAG, "-- Could not compute shared secret for id %d", device->getID());
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

        LOG_P(TAG, "Finished with a prior epoch [Decoded? %d] [Before Previous? %d]", isDecoded, isBeforePrevious);
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

      LOG_P(TAG, "Compute RS Symbol Size - New Best (W = %zu, #Advert = %zu)", bestW, bestNumAdverts);
    }
  }

    LOG_P(TAG, "Done RSSymbolSize (W = %zu)", bestW);
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

  LOG_P("SDDRRadio", "Created new Bloom filter (Advertised %d, Passive %d, Random %d)", 
          numAdvert, numPassive, numRandom);
}

void SDDRRadio::addRecentDevice(EbNDevice *device)
{
  removeRecentDevice(device->getID());

  recentDevices_.push_front(device);
  idToRecentDevices_.insert(make_pair(device->getID(), recentDevices_.begin()));
  LOG_P(TAG, "Recent device %d", device->getID());
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
  LOG_P(TAG, "-- getDeviceEvent device %d", id);
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
