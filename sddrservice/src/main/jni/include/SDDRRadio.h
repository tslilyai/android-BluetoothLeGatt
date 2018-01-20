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
  std::string dhkey_;
  std::string dhpubkey_;
  std::string advert_;

private:
  static const uint32_t EPOCH_INTERVAL = 30000;//TIME_MIN_TO_MS(15);
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
  std::mutex setMutex_;
  RecentDeviceList recentDevices_;
  IDToRecentDeviceMap idToRecentDevices_;
  uint64_t nextDiscover_;
  uint64_t nextChangeEpoch_;
  uint64_t timeDetectedNewDevice_;
  // note that this doesn't prevent other devices from retroactively linking
  // against this one---this just allows this device to retroactively link
  // against any encountered device
  bool allowRetroactiveLinking_;

  EbNDeviceMap<EbNDevice> deviceMap_;
  std::vector<uint8_t> dhPrevSymbols_;
  ECDH dhExchange_;
  std::mutex dhExchangeMutex_;
  size_t advertNum_;
  BitMap lastAdvert_;
  std::list<DiscoverEvent> discovered_;
  EbNHystPolicy hystPolicy_;
  uint64_t rssiReportInterval_;
  Address sddraddr_;

private:
  void setAdvert();
  void shiftAddr();
  bool processAdvert(EbNDevice *device, uint64_t time, const uint8_t *data);
  void processEpochs(EbNDevice *device);
  std::set<DeviceID> handshake(const std::set<DeviceID> &deviceIDs);
  std::string encounterToMsg(const EncounterEvent &event);

  void getDeviceAdvert(EncounterEvent &event, DeviceID id);
  bool getDeviceEvent(EncounterEvent &event, DeviceID id, uint64_t rssiReportInterval);
  DeviceID generateDeviceID();
  EncounterEvent doneWithDevice(DeviceID id);
  void addRecentDevice(EbNDevice *device);
  void removeRecentDevice(DeviceID id);

  void fillBloomFilter(BloomFilter *bloom, const uint8_t *prefix, 
          uint32_t prefixSize, bool includePassive = true);
  void fillBloomFilter(BloomFilter *bloom, const LinkValueList &advertisedSet, 
          const uint8_t *prefix, uint32_t prefixSize, bool includePassive = true);

public: // to be called via JNI
  SDDRRadio(size_t keySize, int adapterID, EbNHystPolicy hystPolicy,
          uint64_t rssiReportInterval);
  void preDiscovery();
  std::vector<std::string> postDiscoveryGetEncounters();
  const Address getSDDRAddr();
  void changeEpoch();
  long processScanResponse(Address sddraddr, int8_t rssi, std::string advert, Address dev_addr);
  std::string computeSecretKey(std::string myDHKey, std::string sha1OtherDHKey, std::string otherDHKey);
  ActionInfo getNextAction();
};

inline DeviceID SDDRRadio::generateDeviceID()
{
  return nextDeviceID_++;
}

#endif // SDDRRADIO_H
