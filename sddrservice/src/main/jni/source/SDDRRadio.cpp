#include "SDDRRadio.h"

using namespace std;

uint64_t sddrStartTimestamp = getTimeMS();

SDDRRadio::SDDRRadio(size_t keySize, int adapterID, EbNHystPolicy hystPolicy, uint64_t rssiReportInterval)
: nextDeviceID_(0),
     keySize_(keySize),
     setMutex_(),
     recentDevices_(),
     idToRecentDevices_(),
     deviceMap_(),
     dhExchange_(keySize),
     dhExchangeMutex_(),
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
	sddraddr_ = Address::newIDWithPartial((uint8_t)dhExchange_.getPublicY() << 5, 0x20);
	setAdvert();
}

const Address SDDRRadio::getSDDRAddr() {
    return sddraddr_;
}


void SDDRRadio::shiftAddr() {
    // get a new random address. uses the 1 bit Y coordinate as part of the
    // address since compressed ECDH keys are actually (keySize + 1) bits long
    uint8_t partial = (uint8_t)dhExchange_.getPublicY() << 5;
    sddraddr_ = Address::newIDWithPartial(partial, 0x20);//TODO just for testing 
    //sddraddr_ = sddraddr_.shiftWithPartial(partial, 0x20);
}

void SDDRRadio::changeEpoch()
{
    lock_guard<mutex> dhExchangeLock(dhExchangeMutex_);

    // Generate a new secret for this epoch's DH exchanges
    dhExchange_.generateSecret();
    setAdvert();
	shiftAddr();
}


/* 
 * DISCOVERY FUNCTIONS 
 * */

void SDDRRadio::preDiscovery()
{
    discovered_.clear();
}

long SDDRRadio::processScanResponse(Address sddrAddr, int8_t rssi, std::string advert)
{
    LOG_P(TAG, "Processing scan response with Addr %s, rssi %d, and data %s", sddrAddr.toString().c_str() , rssi, advert.c_str());
    
    if (!sddrAddr.verifyChecksum()) {
        LOG_P(TAG, "Not an SDDR device, address checksum failed");
        return false;
    }

    uint64_t scanTime = getTimeMS();
    EbNDevice *device = deviceMap_.get(sddrAddr);
    if(device == NULL)
    {
      lock_guard<mutex> setLock(setMutex_);

      device = new EbNDevice(generateDeviceID(), sddrAddr);
      deviceMap_.add(sddrAddr, device);

      LOG_D("ENCOUNTERS_TEST", "-- Discovered new SDDR device (ID %ld, Address %s)", 
              device->getID(), device->getAddress().toString().c_str());
    }

    device->addRSSIMeasurement(scanTime, rssi);
    discovered_.push_back(DiscoverEvent(scanTime, device->getID(), rssi));
    LOG_P(TAG, "-- Discovered device %d", device->getID());
    addRecentDevice(device);
    if (advert.length() > 0)
        device->addAdvert(advert);
	return sddrStartTimestamp + device->getID(); 
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
      getDeviceAdvert(event, ndIt->first);
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

    LOG_P(TAG, "-- Sending %d encounters", messages.size());
    return messages;
}

/* 
 * ENCOUNTER / HANDSHAKE FUNCTIONS 
 */
std::string SDDRRadio::encounterToMsg(const EncounterEvent &event)
{
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
  LOG_P(TAG, "# RSSI Entries = %d", event.rssiEvents.size());
  LOG_P(TAG, "# Adverts = %d", event.adverts.size());

  SDDR::Event_EncounterEvent *encounterEvent = new SDDR::Event_EncounterEvent();
  encounterEvent->set_type((SDDR::Event_EncounterEvent_EventType)event.type);
  encounterEvent->set_time(event.time);
  encounterEvent->set_id(event.id);
  encounterEvent->set_pkid(event.getPKID());
  encounterEvent->set_address(event.address);
  encounterEvent->set_matchingsetupdated(false);

  for(auto it = event.rssiEvents.begin(); it != event.rssiEvents.end(); it++)
  {
    SDDR::Event_EncounterEvent_RSSIEvent *rssiEvent = encounterEvent->add_rssievents();
    rssiEvent->set_time(it->time);
    rssiEvent->set_rssi(it->rssi);
  }

  for(auto it = event.adverts.begin(); it != event.adverts.end(); it++)
  {
      encounterEvent->add_sharedsecrets(it->c_str(), it->size());
  }

  std::string str;
  SDDR::Event fullEvent;
  fullEvent.set_allocated_encounterevent(encounterEvent);
  fullEvent.SerializeToString(&str);
  return str;
}

std::set<DeviceID> SDDRRadio::handshake(const std::set<DeviceID> &deviceIDs)
{
    set<DeviceID> encountered;

    for(auto it = deviceIDs.begin(); it != deviceIDs.end(); it++)
    {
        EbNDevice *device = deviceMap_.get(*it);
        device->setShakenHands(true);
    }

    // Going through all devices to report 'encountered' devices, meaning
    // the devices we have shaken hands with
    for(auto it = deviceMap_.begin(); it != deviceMap_.end(); it++)
    {
        EbNDevice *device = it->second;
        if(device->hasShakenHands())
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

/* 
 * ADVERT PROCESSING AND GENERATION FUNCTIONS
 */
void SDDRRadio::setAdvert()
{
  	size_t messageSize = dhExchange_.getPublicSize();
  	vector<uint8_t> message(messageSize, 0);

  	// Adding the full public key (X and Y coordinates) to the message
  	memcpy(message.data(), dhExchange_.getPublic(), dhExchange_.getPublicSize());

  	unsigned char hash[SHA_DIGEST_LENGTH]; // == 20
  	SHA1(message.data(), messageSize, hash);
    
    dhpubkey_ = std::string((const char*) message.data(), messageSize);
    dhkey_ = dhExchange_.getSerializedKey();

	advert_ = std::string((const char*) hash, SHA_DIGEST_LENGTH);
    LOG_D("c_sddr", "Advert is %s: ", advert_.c_str());
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

void SDDRRadio::getDeviceAdvert(EncounterEvent &event, DeviceID id)
{
  IDToRecentDeviceMap::iterator it = idToRecentDevices_.find(id);
  if(it != idToRecentDevices_.end())
  {
    EbNDevice *device = *it->second;
    device->getEncounterStartAdvert(event);
  }
}

std::string SDDRRadio::computeSecretKey(std::string myDHKey, std::string SHA1DHKey, std::string otherDHKey) {

  	size_t messageSize = dhExchange_.getPublicSize();
  	vector<uint8_t> message(messageSize, 0);

  	memcpy(message.data(), otherDHKey.c_str(), messageSize);

    if (SHA1DHKey.length() != 0) {
        unsigned char hash[SHA_DIGEST_LENGTH]; // == 20
        SHA1(message.data(), messageSize, hash);
        std::string hashStr = std::string((const char*) hash, SHA_DIGEST_LENGTH);
        if (hashStr.compare(SHA1DHKey) != 0) {
            LOG_D("c_SDDR", "UhOh.... hash is %s, otherDHKey is %s", hashStr.c_str(), SHA1DHKey.c_str());
            return std::string("");
        }
    }
    char dest[keySize_ / 8];
    ECDH::computeSharedSecret(
            (char*)dest,
            myDHKey,
            (const uint8_t*)otherDHKey.substr(0, messageSize).c_str(),
            keySize_
    );
    LOG_D("c_sddr", "shared secret!!!! %s ", std::string(dest, keySize_/8).c_str());
    return std::string(dest, keySize_/8);
}
