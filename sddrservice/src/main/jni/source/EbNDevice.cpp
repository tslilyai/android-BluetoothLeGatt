#include "EbNDevice.h"

#include "Logger.h"

extern uint64_t sddrStartTimestamp;

using namespace std;

EbNDevice::EbNDevice(DeviceID id, const Address &address, const LinkValueList &listenSet)
   : id_(id),
     address_(address),
     matching_(listenSet),
     updatedMatching_(false),
     matchingPFalse_(1),
     sharedSecrets_(),
     secretsToReport_(),
     sharedSecretsMutex_(),
     rssiToReport_(),
     lastReportTime_(0),
     confirmed_(false),
     shakenHands_(false),
     reported_(false),
     epochs_(),
     bloomsToReport_()
{
}

EbNDevice::~EbNDevice()
{
}

EbNDevice::Epoch::Epoch(uint32_t advertNum, uint64_t advertTime, const RSMatrix &dhCodeMatrix, const ECDH &dhExchange, bool dhExchangeYCoord)
   : lastAdvertNum(advertNum),
     lastAdvertTime(advertTime),
     dhDecoder(dhCodeMatrix),
     dhExchanges(1, dhExchange),
     dhExchangeYCoord(dhExchangeYCoord),
     blooms(),
     decodeBloomNum(0) 
{
}

void EbNDevice::updateBlooms(const BloomFilter *bloom, const BitMap *prefix, float pFalseDelta)
{
  bloomsToReport_.push_back(BloomInfo(*bloom, *prefix, pFalseDelta));
}

void EbNDevice::updateMatching(const BloomFilter *bloom, const uint8_t *prefix, uint32_t prefixSize)
{
  updateMatching(bloom, prefix, prefixSize, bloom->pFalse());
}

void EbNDevice::updateMatching(const BloomFilter *bloom, const uint8_t *prefix, uint32_t prefixSize, float pFalseDelta)
{
  LinkValueList::iterator it = matching_.begin();
  while(it != matching_.end())
  {
    const LinkValue &value = *it;
    if(!bloom->query(prefix, prefixSize, value.get(), value.size()))
    {
        LOG_D(TAG, "-- BLOOM: Update didn't find %s", 
                BitMap(value.size(), value.get()).toString().c_str())
        it = matching_.erase(it);
        updatedMatching_ = true;
    }
    else
    {
        LOG_D(TAG, "-- BLOOM: Update found %s", 
                BitMap(value.size(), value.get()).toString().c_str());
        it++;
    }
  }
  matchingPFalse_ *= pFalseDelta;
  LOG_D(TAG, "BLOOM: Updated matching set to %d entries (pFalse %g) for id %llu", matching_.size(), matchingPFalse_, sddrStartTimestamp + id_);
}

void EbNDevice::addSharedSecret(const SharedSecret &secret)
{
  bool found = false;
  SharedSecret::Equal equalFunc;

  lock_guard<mutex> sharedSecretsLock(sharedSecretsMutex_);

  for(auto it = sharedSecrets_.begin(); it != sharedSecrets_.end(); it++)
  {
    if(equalFunc(secret, *it))
    {
      if(!it->confirmed && secret.confirmed)
      {
        confirmed_ = true;
        it->confirm(secret.confirmedBy);
        secretsToReport_.push_back(secret);

        LOG_P("EbNDevice", "Updated shared secret \'%s\' for id %d [Confirmed? 1]", secret.value.toString().c_str(), id_);
      }

      found = true;
      break;
    }
  }

  if(!found)
  {
    sharedSecrets_.push_back(secret);
    if(secret.confirmed)
    {
      confirmed_ = true;
      secretsToReport_.push_back(secret);
    }

    LOG_P("EbNDevice", "Added shared secret \'%s\' for id %d [Confirmed? %d]", secret.value.toString().c_str(), id_, secret.confirmed);
  }
}

void EbNDevice::confirmPassive(const BloomFilter *bloom, const uint8_t *prefix, uint32_t prefixSize, float threshold)
{
  confirmPassive(bloom, prefix, prefixSize, threshold, bloom->pFalse());
}

void EbNDevice::confirmPassive(const BloomFilter *bloom, const uint8_t *prefix, uint32_t prefixSize, float threshold, float pFalseDelta)
{
  lock_guard<mutex> sharedSecretsLock(sharedSecretsMutex_);

  for(auto it = sharedSecrets_.begin(); it != sharedSecrets_.end(); it++)
  {
    SharedSecret &secret = *it;
    if(!secret.confirmed && (secret.pFalse > threshold))
    {
      if(bloom->query(prefix, prefixSize, secret.value.get(), secret.value.size()))
      {
        secret.pFalse *= pFalseDelta;
        if(secret.pFalse <= threshold)
        {
          confirmed_ = true;
          secret.confirm(SharedSecret::ConfirmScheme::Passive);
          secretsToReport_.push_back(secret);

          LOG_P("EbNDevice", "Confirmed shared secret \'%s\' for id %d", secret.value.toString().c_str(), id_);
        }
      }
      else
      {
        secret.pFalse = 1;
      }

      if(!secret.confirmed)
      {
        LOG_P("EbNDevice", "Shared secret \'%s\' for id %d has pFalse %g", secret.value.toString().c_str(), id_, secret.pFalse);
      }
    }
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
    dest.type = EncounterEvent::Ended;
    dest.id = id_;
    dest.address = address_.toString();
    dest.matchingSetUpdated = false;

    if(!rssiToReport_.empty())
    {
      dest.rssiEvents = rssiToReport_;
      rssiToReport_.clear();
    }

    success = true;
  }
  else
  {
    lock_guard<mutex> sharedSecretsLock(sharedSecretsMutex_);

    const bool reportSecrets = !secretsToReport_.empty();
    const bool reportMatching = updatedMatching_;
    bool reportBlooms;
    if (retroactive) {
        reportBlooms = !bloomsToReport_.empty();
    } else {
        reportBlooms = false;
    }
    const bool reportRSSI = !rssiToReport_.empty() && ((getTimeMS() - lastReportTime_) > rssiReportingInterval);

    const bool isUpdated = confirmed_ && shakenHands_ && (reportSecrets || reportMatching || reportRSSI || reportBlooms);
    if(isUpdated)
    {
      dest.type = (reported_ ? EncounterEvent::Updated : EncounterEvent::Started);
      dest.id = id_;
      dest.address = address_.toString();

      if(updatedMatching_)
      {
        dest.matching = matching_;
        dest.matchingSetUpdated = true;
        updatedMatching_ = false;
      }
      else
      {
        dest.matchingSetUpdated = false;
      }

      if(!secretsToReport_.empty())
      {
        dest.sharedSecrets = secretsToReport_;
        dest.sharedSecretsUpdated = true;
        secretsToReport_.clear();
      }
      else
      {
        dest.sharedSecretsUpdated = false;
      }

      if(!bloomsToReport_.empty())
      {
          dest.blooms = bloomsToReport_;
          dest.bloomsUpdated = true;
          bloomsToReport_.clear();
      }
      else
      {
          dest.bloomsUpdated = false;
      }

      if(!rssiToReport_.empty())
      {
        dest.rssiEvents = rssiToReport_;
        rssiToReport_.clear();
      }

      reported_ = true;
      lastReportTime_ = getTimeMS();
 
   }

    success = isUpdated;
  }

  return success;
}

