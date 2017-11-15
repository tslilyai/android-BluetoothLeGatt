#ifndef EBNEVENTS_H
#define EBNEVENTS_H

#include <cstdint>
#include <list>
#include <string>

#include "LinkValue.h"
#include "BloomFilter.h"

typedef int32_t DeviceID;

extern uint64_t sddrStartTimestamp;

struct BloomInfo
{
  BloomFilter bloom;
  BitMap prefix;
  float pFalseDelta;

  BloomInfo(BloomFilter bloom, BitMap prefix, float pfalse) :
    bloom(bloom), prefix(prefix), pFalseDelta(pfalse) {};
};

struct DiscoverEvent
{
  uint64_t time;
  DeviceID id;
  int8_t rssi;

  struct Compare
  {
    bool operator ()(const DiscoverEvent &a, const DiscoverEvent &b) const
    {
      return a.id < b.id;
    }
  };

  DiscoverEvent(uint64_t time, DeviceID id, int8_t rssi)
     : time(time),
       id(id),
       rssi(rssi)
  {
  }
};

struct RSSIEvent
{
  uint64_t time;
  int8_t rssi;

  RSSIEvent(uint64_t time, int8_t rssi)
     : time(time),
       rssi(rssi)
  {
  }
};

struct EncounterEvent
{
  enum Type
  {
    Started = 0,
    Updated = 1,
    Ended = 2,
    UnconfirmedStarted = 3
  };

  Type type;
  uint64_t time;
  DeviceID id;
  std::string address;
  std::list<RSSIEvent> rssiEvents;
  std::list<LinkValue> matching;
  std::list<SharedSecret> sharedSecrets;
  std::list<BloomInfo> blooms;
  bool matchingSetUpdated;
  bool sharedSecretsUpdated;
  bool bloomsUpdated;

  EncounterEvent(uint64_t time)
     : time(time),
       matchingSetUpdated(false),
       sharedSecretsUpdated(false),
       bloomsUpdated(false)
  {
  }

  EncounterEvent(Type type, uint64_t time, DeviceID id)
     : type(type),
       time(time),
       id(id),
       matchingSetUpdated(false),
       sharedSecretsUpdated(false),
       bloomsUpdated(false)
  {
  }

  // Unique handle to be used as database primary key
  uint64_t getPKID() const
  {
      return sddrStartTimestamp + id;
  }
};

#endif // EBNEVENTS_H
