#ifndef EBNDEVICEBT4_H
#define EBNDEVICEBT4_H

#include <cstdint>
#include <list>

#include "EbNDevice.h"
#include "ECDH.h"
#include "SegmentedBloomFilter.h"
#include "RSErasureDecoder.h"

class EbNRadioBT4;

class EbNDeviceBT4 : public EbNDevice
{
  friend class SDDRRadio;

private:
  typedef std::list<std::pair<size_t, SegmentedBloomFilter> > BloomList;

  struct Epoch
  {
    uint8_t *advert;
    std::list<ECDH> dhExchanges;
    bool dhExchangeYCoord;

    Epoch(uint8_t *advert, const ECDH &dhExchange, bool dhExchangeYCoord);
  };

private:
  std::list<Epoch> epochs_;

public:
  EbNDeviceBT4(DeviceID id, const Address &address, const LinkValueList &listenSet);
};

#endif // EBNDEVICEBT4_H

