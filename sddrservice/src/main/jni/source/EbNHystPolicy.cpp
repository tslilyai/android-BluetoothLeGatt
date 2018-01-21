#include "EbNHystPolicy.h"

#include "Logger.h"

using namespace std;

const char *EbNHystPolicy::schemeStrings[] = { "Standard", "Immediate", "", "ImmediateNoMem" };

EbNHystPolicy::EbNHystPolicy(Scheme scheme, uint64_t minStartTime, uint64_t maxStartTime, size_t startSeen, uint64_t endTime, int8_t rssiThreshold)
   : scheme_(scheme),
     minStartTime_(minStartTime),
     maxStartTime_(maxStartTime),
     startSeen_(startSeen),
     endTime_(endTime),
     rssiThreshold_(rssiThreshold)
{
}

set<DeviceID> EbNHystPolicy::discovered(const list<DiscoverEvent> &events, list<pair<DeviceID, uint64_t>>& newlyDiscovered)
{
  uint64_t time = getTimeMS();
  set<DeviceID> toHandshake;

  for(auto discIt = events.cbegin(); discIt != events.cend(); discIt++)
  {
    const DiscoverEvent &discovery = *discIt;

    LOG_P("EbNHystPolicy", "[D] [%" PRIu64 "] %d %d", discovery.time, discovery.id, discovery.rssi);

    DeviceInfoMap::iterator infoIt = deviceInfo_.find(discovery.id);
    // we haven't seen this device before! add it to newly discovered devices
    if(infoIt == deviceInfo_.end())
    {
        infoIt = deviceInfo_.insert(make_pair(discovery.id, DeviceInfo(time))).first;
        newlyDiscovered.push_back(make_pair(discovery.id, time));
    }
    DeviceInfo &info = infoIt->second;
    info.lastTime = time;
    if(discovery.rssi > rssiThreshold_)
    {
        info.seen++;
    }
    switch(info.state)
    {
      case HystState::Discovered:
          // only handshake if we've seen the new device for x amount of time
        if((info.seen >= startSeen_) && ((info.lastTime - info.firstTime) >= minStartTime_))
        {
          info.state = HystState::Encountered;
          toHandshake.insert(discovery.id);

          LOG_P("EbNHystPolicy", "Changing state for device %d to 'Encountered'", discovery.id);
          LOG_P("EbNHystPolicy", "[EStart] [%" PRIu64 "] %d", discovery.time, discovery.id);
        }
        break;
      case HystState::Encountered:
        toHandshake.insert(discovery.id);
        break;
      }
    }
    return toHandshake;
}

void EbNHystPolicy::encountered(const std::set<DeviceID> &devices)
{
  uint64_t time = getTimeMS();

  // Note: It *is* possible, when using active or hybrid confirmation, that we
  // confirm a device which we previously have not discovered. Therefore, we
  // handle this case here and insert this new entry if not found.
  for(auto devIt = devices.cbegin(); devIt != devices.cend(); devIt++)
  {
    const DeviceID &devID = *devIt;

    DeviceInfoMap::iterator infoIt = deviceInfo_.find(devID);
    if(infoIt == deviceInfo_.end())
    {
      infoIt = deviceInfo_.insert(make_pair(devID, DeviceInfo(time, HystState::Encountered))).first;
    }
    else
    {
      infoIt->second.state = HystState::Encountered;
    }
  }
}

list<pair<DeviceID, uint64_t> > EbNHystPolicy::checkExpired()
{
  uint64_t time = getTimeMS();
  list<pair<DeviceID, uint64_t>> toRemove;

  DeviceInfoMap::iterator it = deviceInfo_.begin();
  while(it != deviceInfo_.end())
  {
    DeviceID id = it->first;
    const DeviceInfo &info = it->second;

    bool remove = false;
    switch(info.state)
    {
    case HystState::Discovered:
      if((time - info.lastTime) > maxStartTime_)
      {
        remove = true;
      }
      break;
    case HystState::Encountered:
      if((time - info.lastTime) > endTime_)
      {
        remove = true;
      }
      break;
    }

    if(remove)
    {
      toRemove.push_back(make_pair(id, info.lastTime));
      it = deviceInfo_.erase(it);
      LOG_P("EbNHystPolicy", "[EEnd] [%" PRIu64 "] %d", info.lastTime, id);
    }
    else
    {
      it++;
    }
  }

  return toRemove;
}
