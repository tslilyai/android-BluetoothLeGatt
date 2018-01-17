#ifndef SDDRRADIO_H
#define SDDRRADIO_H

#include <cstdint>
#include <list>
#include <memory>
#include <mutex>
#include <queue>
#include <set>
#include <unordered_map>
#include <vector>

#include "CompileMath.h"
#include "BloomFilter.h"
#include "SegmentedBloomFilter.h"
#include "EbNDevice.h"
#include "EbNHystPolicy.h"
#include "EbNDeviceMap.h"
#include "ECDH.h"
#include "Logger.h"
#include "RSErasureEncoder.h"
#include "Timing.h"
#include "sddr.pb.h"
#include <jni.h>

class SDDRRadio 
{
public:
  struct Action_
  {
    enum Type
    {
      ChangeEpoch,
      Discover,
    };
  };
  typedef Action_::Type Action;

  struct ActionInfo
  {
    Action action;
    int64_t timeUntil;

    ActionInfo(Action action, int64_t timeUntil)
       : action(action),
         timeUntil(timeUntil)
    {
    }
  };
public: 
  static const uint64_t ADVERT_LEN = 22;

private:
  static const uint32_t EPOCH_INTERVAL = TIME_MIN_TO_MS(15);//15);
  static const uint64_t SCAN_INTERVAL_ENCOUNTERS = 30000; // ms
  static const uint64_t SCAN_INTERVAL_IDLE = 30000; // ms
  static const uint64_t TIME_IDLE_MODE = 300000; // ms

protected:
  typedef std::list<EbNDevice *> RecentDeviceList;
  typedef std::unordered_map<DeviceID, RecentDeviceList::iterator> IDToRecentDeviceMap;
  typedef std::priority_queue<SharedSecret, std::vector<SharedSecret>, SharedSecret::Compare> 
      SharedSecretQueue;

protected:
  DeviceID nextDeviceID_;
  size_t keySize_;
  LinkValueList advertisedSet_;
  LinkValueList listenSet_;
  std::mutex setMutex_;
  RecentDeviceList recentDevices_;
  IDToRecentDeviceMap idToRecentDevices_;
  uint64_t nextDiscover_;
  uint64_t nextChangeEpoch_;
  uint64_t timeDetectedNewDevice_;
  uint64_t timeDetectedUnconfirmedDevice_;
  // note that this doesn't prevent other devices from retroactively linking
  // against this one---this just allows this device to retroactively link
  // against any encountered device
  bool allowRetroactiveLinking_;

  EbNDeviceMap<EbNDevice> deviceMap_;
  RSMatrix dhCodeMatrix_;
  RSErasureEncoder dhEncoder_;
  std::vector<uint8_t> dhPrevSymbols_;
  ECDH dhExchange_;
  std::mutex dhExchangeMutex_;
  size_t advertNum_;
  BitMap lastAdvert_;
  SegmentedBloomFilter advertBloom_;
  size_t advertBloomNum_;
  std::list<DiscoverEvent> discovered_;
  EbNHystPolicy hystPolicy_;
  uint64_t rssiReportInterval_;

private:
  BitMap generateAdvert(size_t advertNum);
  bool processAdvert(EbNDevice *device, uint64_t time, const uint8_t *data);
  void processEpochs(EbNDevice *device);
  std::set<DeviceID> handshake(const std::set<DeviceID> &deviceIDs);
  std::string encounterToMsg(const EncounterEvent &event);

  ConfirmScheme::Type getHandshakeScheme();
  bool getDeviceEvent(EncounterEvent &event, DeviceID id, uint64_t rssiReportInterval);
  DeviceID generateDeviceID();
  EncounterEvent doneWithDevice(DeviceID id);
  void addRecentDevice(EbNDevice *device);
  void removeRecentDevice(DeviceID id);

  void fillBloomFilter(BloomFilter *bloom, const uint8_t *prefix, 
          uint32_t prefixSize, bool includePassive = true);
  void fillBloomFilter(BloomFilter *bloom, const LinkValueList &advertisedSet, 
          const uint8_t *prefix, uint32_t prefixSize, bool includePassive = true);
  static size_t computeRSSymbolSize(size_t keySize, size_t advertBits);

public: // to be called via JNI
  SDDRRadio(size_t keySize, ConfirmScheme confirmScheme, 
          MemoryScheme memoryScheme, bool retroActive,
          int adapterID, EbNHystPolicy hystPolicy,
          uint64_t rssiReportInterval);
  char const* changeAndGetAdvert();
  void preDiscovery();
  std::vector<std::string> postDiscoveryGetEncounters();
  const Address getRandomAddr();
  void changeEpoch();
  bool processScanResponse(Address addr, int8_t rssi, uint8_t* data);
  ActionInfo getNextAction();
  void setAdvertisedSet(const LinkValueList &advertisedSet);
  void setListenSet(const LinkValueList &listenSet);
  bool getRetroactiveMatches(LinkValueList& matching, std::list<BloomInfo>& blooms);
};

inline DeviceID SDDRRadio::generateDeviceID()
{
  return nextDeviceID_++;
}

#endif // SDDRRADIO_H
