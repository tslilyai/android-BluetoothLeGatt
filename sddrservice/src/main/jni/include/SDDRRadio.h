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

#define ADVERT_LEN 22

class SDDRRadio 
{
public:
  struct ConfirmScheme
  {
    enum Type
    {
      None = 0,
      Passive = 1,
      Active = 2,
      Hybrid = 3,
      END
    } type;
    float threshold;
  };
  static const char *confirmSchemeStrings[];
  static ConfirmScheme::Type stringToConfirmScheme(const char* name);

  struct MemoryScheme_
  {
    enum Type
    {
      Standard,
      NoMemory
    };
  };
  typedef MemoryScheme_::Type MemoryScheme;
  static const char *memorySchemeStrings[];

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

  static ConfirmScheme getDefaultConfirmScheme();

private:
  const size_t RS_W;
  const size_t RS_K;
  const size_t RS_M;
  const size_t BF_SM;
  const size_t BF_K;
  const size_t BF_B;

  static const uint32_t BF_N = 256;
  static const uint32_t BF_N_PASSIVE = 128;
  static const uint32_t EPOCH_INTERVAL = TIME_MIN_TO_MS(15);//15);

  static const uint16_t ADVERT_MIN_INTERVAL = 650; // ms
  static const uint16_t ADVERT_MAX_INTERVAL = 700; // ms
  static const uint16_t SCAN_INTERVAL = 15000;//ms 
  static const size_t ADV_N = 2 * ((EPOCH_INTERVAL + (SCAN_INTERVAL - 1)) / SCAN_INTERVAL);
  static const size_t ADV_N_LOG2 = CLog<ADV_N>::value;

  static const size_t ACTIVE_BF_M = 1108;
  static const size_t ACTIVE_BF_K = 3;
  static const bool ACTIVE_BF_ENABLED = true;

protected:
  typedef std::list<EbNDevice *> RecentDeviceList;
  typedef std::unordered_map<DeviceID, RecentDeviceList::iterator> IDToRecentDeviceMap;
  typedef std::priority_queue<SharedSecret, std::vector<SharedSecret>, SharedSecret::Compare> 
      SharedSecretQueue;

protected:
  DeviceID nextDeviceID_;
  size_t keySize_;
  ConfirmScheme confirmScheme_;
  MemoryScheme memoryScheme_;
  LinkValueList advertisedSet_;
  LinkValueList listenSet_;
  std::mutex setMutex_;
  RecentDeviceList recentDevices_;
  IDToRecentDeviceMap idToRecentDevices_;
  uint64_t nextDiscover_;
  uint64_t nextChangeEpoch_;
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
  void processScanResponse(Address addr, int8_t rssi, uint8_t* data);
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

