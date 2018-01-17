#include "Address.h"
#include "Logger.h"

#include <stdlib.h>

using namespace std;

Address::Address()
   : value_()
{
}

Address::Address(size_t size, const uint8_t *value)
   : value_(size, 0)
{
  if(value != NULL)
  {
    memcpy(value_.data(), value, size);
  }
}

Address Address::newIDWithPartial(uint8_t partial, uint8_t mask)
{
  Address address(ADDR_LEN);

  for(size_t i = 0; i < ADDR_LEN; i++) 
  {
    address.value_[i] = rand() & 0xFF;
  }

  address.value_[0] = (address.value_[0] & ~mask) | (partial& mask);
  address.value_[(ADDR_LEN / 2) - 1] &= 0xF0;
  address.value_[(ADDR_LEN / 2) - 1] |= computeChecksum(&address.value_[0], ADDR_LEN / 2);
  address.value_[ADDR_LEN - 1] &= 0xF0;
  address.value_[ADDR_LEN - 1] |= computeChecksum(&address.value_[ADDR_LEN / 2], ADDR_LEN / 2); 

  return address;
}

string Address::toCharString() const
{
  const char* data = (const char*)value_.data();
  return string(data);
}

string Address::toString() const
{
  static const char hexTable[] = "0123456789ABCDEF";

  string str((value_.size() * 3) - 1, ':');
  size_t i;
  for(i = 0; i < value_.size(); i++)
  {
    str[3 * i] = hexTable[value_[i] >> 4];
    str[(3 * i) + 1] = hexTable[value_[i] & 0x0F];
  }

  return str;
}

bool Address::verifyChecksum() const
{
  bool success = false;

  size_t halfSize = value_.size() / 2; 
  if((value_[halfSize - 1] & 0x0F) == computeChecksum(&value_[0], halfSize) &&
     (value_[value_.size() - 1] & 0x0F) == computeChecksum(&value_[halfSize], halfSize)) 
  {
    return true;
  }

  return success;
}

uint8_t Address::computeChecksum(const uint8_t *part, size_t size)
{
  uint8_t checksum = 0;

  size_t i;
  for(i = 0; i < size - 1; i++) 
  {
    checksum += (part[i] >> 4) + (part[i] & 0x0F); 
  }
  checksum += (part[i] >> 4); 

  return checksum & 0x0F;
}
