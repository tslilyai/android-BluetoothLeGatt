#ifndef CONFIG_H
#define CONFIG_H

#include <cstdint>

#include "SDDRRadio.h"
#include "EbNHystPolicy.h"

struct Config
{
  struct Radio
  {
    size_t keySize;
    SDDRRadio::ConfirmScheme confirm;
    SDDRRadio::MemoryScheme memory;
    bool retroactive;
  } radio;

  struct HystPolicy
  {
    EbNHystPolicy::Scheme scheme;
    uint64_t minStartTime; // ms
    uint64_t maxStartTime; // ms
    size_t startSeen;
    uint64_t endTime;      // ms
    int8_t rssiThreshold;
  } hyst;

  struct Reporting
  {
    uint64_t rssiInterval; // ms
  } reporting;

  void dump() const;
};

constexpr Config configDefaults =
{
  {224, {SDDRRadio::ConfirmScheme::Passive, 0.05}, SDDRRadio::MemoryScheme::Standard, 
  true /*retroactive linking*/},
  {EbNHystPolicy::Scheme::Standard, TIME_MIN_TO_MS(2), TIME_MIN_TO_MS(5), 2, TIME_MIN_TO_MS(10), -85},
  {TIME_MIN_TO_MS(1)}
};
  
//{192, {SDDRRadio::ConfirmScheme::Passive, 0.05}, SDDRRadio::MemoryScheme::Standard},

#endif // CONFIG_H
