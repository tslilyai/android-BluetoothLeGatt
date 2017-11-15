#include <cstdint>
#include <cstdio>
#include <cutils/properties.h>
#include <endian.h>
#include <functional>
#include <iostream>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <sys/socket.h>
#include <sys/un.h>
#include <stdexcept>
#include <thread>
#include <unistd.h>

#include "Config.h"
#include "SDDRRadio.h"
#include "Logger.h"

void initialize()
{
  FILE *urand = fopen("/dev/urandom", "r");
  unsigned int seed = 0;
  for(int b = 0; b < sizeof(seed); b++)
  {
    seed <<= 8;
    seed |= (uint8_t)getc(urand);
  }
  srand(seed);
}

int main(int argc, char **argv)
{
    EbNHystPolicy hystPolicy (
        EbNHystPolicy::Scheme::Standard, 
        TIME_MIN_TO_MS(2),
        TIME_MIN_TO_MS(5), 2, TIME_MIN_TO_MS(10), -85);
    SDDRRadio radio (192, 
             {SDDRRadio::ConfirmScheme::Passive, 0.05}, 
             SDDRRadio::MemoryScheme::Standard,
             0, hystPolicy, 10);
    return 0;
}
