#ifndef EBNDEVICE_H
#define EBNDEVICE_H

#include <algorithm>
#include <cstdint>
#include <list>
#include <mutex>
#include <string>
#include <vector>

#include "Address.h"
#include "BloomFilter.h"
#include "EbNEvents.h"
#include "LinkValue.h"
#include "SharedArray.h"
#include "ECDH.h"
#include "SegmentedBloomFilter.h"
#include "RSErasureDecoder.h"


class EbNDevice
{
  friend class SDDRRadio;

private:
  DeviceID id_;
  Address address_;
  std::list<std::string> adverts_;
  std::list<std::string> adverts_to_report_;
  std::mutex advertsMutex_;
  std::list<RSSIEvent> rssiToReport_;
  uint64_t lastReportTime_;
  bool confirmed_;
  bool shakenHands_;

public:
  EbNDevice(DeviceID id, const Address &address);
  ~EbNDevice();

  DeviceID getID() const;

  const Address& getAddress() const;
  virtual void setAddress(const Address &address);

  std::list<std::string> getAdverts();
  void addAdvert(const std::string advert);

  void setShakenHands(bool value);
  bool hasShakenHands() const;

  void addRSSIMeasurement(uint64_t time, uint8_t rssi);

  bool getEncounterInfo(EncounterEvent &dest, bool expired = false, bool retroactive = true);
  bool getEncounterInfo(EncounterEvent &dest, uint64_t rssiReportingInterval, bool expired = false, bool retroactive = true);
  void getEncounterStartAdvert(EncounterEvent &dest);
};

inline DeviceID EbNDevice::getID() const
{
  return id_;
}

inline const Address& EbNDevice::getAddress() const
{
  return address_;
}

inline void EbNDevice::setAddress(const Address &address)
{
  address_ = address;
}

inline std::list<std::string> EbNDevice::getAdverts()
{
  std::lock_guard<std::mutex> advertsLock(advertsMutex_);
  return adverts_;
}

inline void EbNDevice::setShakenHands(bool value)
{
  shakenHands_ = value;
}

inline bool EbNDevice::hasShakenHands() const
{
  return shakenHands_;
}

inline void EbNDevice::addRSSIMeasurement(uint64_t time, uint8_t rssi)
{
  rssiToReport_.push_back(RSSIEvent(time, rssi));
}

#endif // EBNDEVICE_H
