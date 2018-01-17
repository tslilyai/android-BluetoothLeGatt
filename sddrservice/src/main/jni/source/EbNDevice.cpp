#include "EbNDevice.h"

#include "Logger.h"

extern uint64_t sddrStartTimestamp;

using namespace std;

EbNDevice::EbNDevice(DeviceID id, const Address &address)
   : id_(id),
     address_(address),
     adverts_(),
     adverts_to_report_(),
     advertsMutex_(),
     rssiToReport_(),
     lastReportTime_(0),
     shakenHands_(false),
     reported_(false)
{
}

EbNDevice::~EbNDevice()
{
}

void EbNDevice::addAdvert(const std::string advert)
{
    bool found = false;
    lock_guard<mutex> advertLock(advertsMutex_);

    for(auto it = adverts_.begin(); it != adverts_.end(); it++)
    {
        if (it->compare(advert) == 0)
        {
            found = true;
            break;
        }
    }
    if (!found) {
        adverts_.push_back(advert);
        adverts_to_report_.push_back(advert);
        LOG_P("EbNDevice", "Found advert \'%s\' for id %d", advert.c_str(), id_);
    }
}

bool EbNDevice::getEncounterInfo(EncounterEvent &dest, bool expired, bool retroactive)
{
  return getEncounterInfo(dest, numeric_limits<uint64_t>::max(), expired, retroactive);
}

bool EbNDevice::getEncounterInfo(EncounterEvent &dest, uint64_t rssiReportingInterval, bool expired, bool retroactive)
{
  bool success = false;

  if(expired)
  {
    LOG_P(TAG, "getEncounterInfo -- expired");
    dest.type = EncounterEvent::Ended;
    dest.id = id_;
    dest.address = address_.toString();

    if(!rssiToReport_.empty())
    {
      dest.rssiEvents = rssiToReport_;
      rssiToReport_.clear();
    }

    success = true;
  }
  else
  {
    lock_guard<mutex> advertsLock(advertsMutex_);
    
    const bool reportRSSI = !rssiToReport_.empty() && ((getTimeMS() - lastReportTime_) > rssiReportingInterval);
    const bool reportAdverts = !adverts_to_report_.empty();
    const bool isUpdated = shakenHands_ && (reportRSSI || reportAdverts);

    LOG_P(TAG, "getEncounterInfo -- Updated ? %d", isUpdated);
    if(isUpdated)
    {
      dest.type = (reported_ ? EncounterEvent::Updated : EncounterEvent::Started);
      dest.id = id_;
      dest.address = address_.toString();

      if(!rssiToReport_.empty())
      {
        dest.rssiEvents = rssiToReport_;
        rssiToReport_.clear();
      }
      if (!adverts_to_report_.empty()) {
          dest.adverts = adverts_;
          dest.advertsUpdated = true;
          adverts_to_report_.clear();
      }

      reported_ = true;
      lastReportTime_ = getTimeMS();
   }

    success = isUpdated;
  }

  return success;
}
