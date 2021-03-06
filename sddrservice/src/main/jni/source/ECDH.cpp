#include "ECDH.h"
#include "Logger.h"

using namespace std;

ECDH::ECDH()
   : keySize_(0),
     secret_(NULL, &EC_KEY_free),
     localPublic_()
{
}

ECDH::ECDH(size_t keySize)
   : keySize_(keySize),
     secret_(NULL, &EC_KEY_free),
     localPublic_(((keySize + 7) / 8) + 1)
{
  LOG_P("SDDR", "Initializing ECDH");

  /*
   * TODO Figure out why 192-key curve is not supported
   */
  switch(keySize)
  {
  case 192:
    secret_.reset(EC_KEY_new_by_curve_name(NID_X9_62_prime192v2), &EC_KEY_free); // NIST P-192 Curve
    break;
  case 224:
    secret_.reset(EC_KEY_new_by_curve_name(NID_secp224r1), &EC_KEY_free);        // NIST P-224 Curve
    break;
  case 256:
    secret_.reset(EC_KEY_new_by_curve_name(NID_X9_62_prime256v1), &EC_KEY_free); // NIST P-256 Curve
    break;
  case 384:
    secret_.reset(EC_KEY_new_by_curve_name(NID_secp384r1), &EC_KEY_free);        // NIST P-384 Curve
    break;
  default:
    LOG_P(TAG, "invalid keysize");
    throw std::runtime_error("Invalid KeySize - Select from {192,224,256,384}");
  }
  if(secret_.get() == NULL)
  {
    LOG_P(TAG, "could not get secret");
    throw std::runtime_error("Selected curve is not supported");
  }

  LOG_P("SDDR", "Generating secret");
  generateSecret();
  LOG_P("SDDR", "ECDH initialized");
}

void ECDH::generateSecret()
{
  secret_.reset(EC_KEY_dup(secret_.get()), &EC_KEY_free);
  EC_KEY_generate_key(secret_.get());
  EC_POINT_point2oct(EC_KEY_get0_group(secret_.get()), EC_KEY_get0_public_key(secret_.get()), POINT_CONVERSION_COMPRESSED, localPublic_.data(), localPublic_.size(), NULL);
}

std::string ECDH::getSerializedKey() {
    int len = i2d_ECPrivateKey(secret_.get(), NULL);    
    unsigned char derkey[len];
    unsigned char * derkeyPtr = (unsigned char*) derkey;
    i2d_ECPrivateKey(secret_.get(), &derkeyPtr);
    return std::string((const char*)derkey, len);
}

bool ECDH::computeSharedSecret(SharedSecret &dest, const uint8_t *remotePublicX, bool remotePublicY) const
{
  unique_ptr<uint8_t[]> remotePublic(new uint8_t[(keySize_ / 8) + 1]);
  remotePublic[0] = remotePublicY ? 0x03 : 0x02;
  memcpy(remotePublic.get() + 1, remotePublicX, (keySize_ / 8));

  return computeSharedSecret(dest, remotePublic.get());
}

bool ECDH::computeSharedSecret(SharedSecret &dest, const uint8_t *remotePublic) const
{
  bool success = false;

  const EC_GROUP *group = EC_KEY_get0_group(secret_.get());
  ECPointPtr remotePublicPoint(EC_POINT_new(group), &EC_POINT_free);
  if(EC_POINT_oct2point(group, remotePublicPoint.get(), remotePublic, (keySize_ / 8) + 1, NULL))
  {
    dest.value = LinkValue(new uint8_t[keySize_ / 8], keySize_ / 8);
    if(ECDH_compute_key((void *)dest.value.get(), keySize_ / 8, remotePublicPoint.get(), secret_.get(), derivateKey))
    {
      success = true;
    }
  }

  return success;
}

bool ECDH::computeSharedSecret(char* dest, std::string dhKey, const uint8_t *remotePublic, size_t keySize) 
{
  bool success = false;

  const unsigned char* dhKeyBytes = (const unsigned char*)dhKey.c_str();
  EC_KEY* myKey = d2i_ECPrivateKey(NULL, &dhKeyBytes, dhKey.length());
  const EC_GROUP *group = EC_KEY_get0_group(myKey);
  ECPointPtr remotePublicPoint(EC_POINT_new(group), &EC_POINT_free);
  if(EC_POINT_oct2point(group, remotePublicPoint.get(), remotePublic, (keySize / 8) + 1, NULL))
  {
    if(ECDH_compute_key((void *)dest, keySize / 8, remotePublicPoint.get(), myKey, derivateKey))
    {
      success = true;
    }
  }

  return success;
}

void* ECDH::derivateKey(const void *in, size_t inLength, void *out, size_t *outLength)
{
  uint8_t buffer[SHA256_DIGEST_LENGTH];
  SHA256((uint8_t *)in, inLength, buffer);
  memcpy(out, buffer, *outLength);

  return out;
}

