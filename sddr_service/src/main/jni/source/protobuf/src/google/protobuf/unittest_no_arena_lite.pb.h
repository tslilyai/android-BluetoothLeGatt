// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/protobuf/unittest_no_arena_lite.proto

#ifndef PROTOBUF_google_2fprotobuf_2funittest_5fno_5farena_5flite_2eproto__INCLUDED
#define PROTOBUF_google_2fprotobuf_2funittest_5fno_5farena_5flite_2eproto__INCLUDED

#include <string>

#include <google/protobuf/stubs/common.h>

#if GOOGLE_PROTOBUF_VERSION < 3004000
#error This file was generated by a newer version of protoc which is
#error incompatible with your Protocol Buffer headers.  Please update
#error your headers.
#endif
#if 3004000 < GOOGLE_PROTOBUF_MIN_PROTOC_VERSION
#error This file was generated by an older version of protoc which is
#error incompatible with your Protocol Buffer headers.  Please
#error regenerate this file with a newer version of protoc.
#endif

#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/arena.h>
#include <google/protobuf/arenastring.h>
#include <google/protobuf/generated_message_table_driven.h>
#include <google/protobuf/generated_message_util.h>
#include <google/protobuf/metadata_lite.h>
#include <google/protobuf/message_lite.h>
#include <google/protobuf/repeated_field.h>  // IWYU pragma: export
#include <google/protobuf/extension_set.h>  // IWYU pragma: export
// @@protoc_insertion_point(includes)
namespace protobuf_unittest_no_arena {
class ForeignMessageLite;
class ForeignMessageLiteDefaultTypeInternal;
extern ForeignMessageLiteDefaultTypeInternal _ForeignMessageLite_default_instance_;
}  // namespace protobuf_unittest_no_arena

namespace protobuf_unittest_no_arena {

namespace protobuf_google_2fprotobuf_2funittest_5fno_5farena_5flite_2eproto {
// Internal implementation detail -- do not call these.
struct TableStruct {
  static const ::google::protobuf::internal::ParseTableField entries[];
  static const ::google::protobuf::internal::AuxillaryParseTableField aux[];
  static const ::google::protobuf::internal::ParseTable schema[];
  static const ::google::protobuf::uint32 offsets[];
  static const ::google::protobuf::internal::FieldMetadata field_metadata[];
  static const ::google::protobuf::internal::SerializationTable serialization_table[];
  static void InitDefaultsImpl();
};
void AddDescriptors();
void InitDefaults();
}  // namespace protobuf_google_2fprotobuf_2funittest_5fno_5farena_5flite_2eproto

// ===================================================================

class ForeignMessageLite : public ::google::protobuf::MessageLite /* @@protoc_insertion_point(class_definition:protobuf_unittest_no_arena.ForeignMessageLite) */ {
 public:
  ForeignMessageLite();
  virtual ~ForeignMessageLite();

  ForeignMessageLite(const ForeignMessageLite& from);

  inline ForeignMessageLite& operator=(const ForeignMessageLite& from) {
    CopyFrom(from);
    return *this;
  }
  #if LANG_CXX11
  ForeignMessageLite(ForeignMessageLite&& from) noexcept
    : ForeignMessageLite() {
    *this = ::std::move(from);
  }

  inline ForeignMessageLite& operator=(ForeignMessageLite&& from) noexcept {
    if (GetArenaNoVirtual() == from.GetArenaNoVirtual()) {
      if (this != &from) InternalSwap(&from);
    } else {
      CopyFrom(from);
    }
    return *this;
  }
  #endif
  inline const ::std::string& unknown_fields() const {
    return _internal_metadata_.unknown_fields();
  }
  inline ::std::string* mutable_unknown_fields() {
    return _internal_metadata_.mutable_unknown_fields();
  }

  static const ForeignMessageLite& default_instance();

  static inline const ForeignMessageLite* internal_default_instance() {
    return reinterpret_cast<const ForeignMessageLite*>(
               &_ForeignMessageLite_default_instance_);
  }
  static PROTOBUF_CONSTEXPR int const kIndexInFileMessages =
    0;

  void Swap(ForeignMessageLite* other);
  friend void swap(ForeignMessageLite& a, ForeignMessageLite& b) {
    a.Swap(&b);
  }

  // implements Message ----------------------------------------------

  inline ForeignMessageLite* New() const PROTOBUF_FINAL { return New(NULL); }

  ForeignMessageLite* New(::google::protobuf::Arena* arena) const PROTOBUF_FINAL;
  void CheckTypeAndMergeFrom(const ::google::protobuf::MessageLite& from)
    PROTOBUF_FINAL;
  void CopyFrom(const ForeignMessageLite& from);
  void MergeFrom(const ForeignMessageLite& from);
  void Clear() PROTOBUF_FINAL;
  bool IsInitialized() const PROTOBUF_FINAL;

  size_t ByteSizeLong() const PROTOBUF_FINAL;
  bool MergePartialFromCodedStream(
      ::google::protobuf::io::CodedInputStream* input) PROTOBUF_FINAL;
  void SerializeWithCachedSizes(
      ::google::protobuf::io::CodedOutputStream* output) const PROTOBUF_FINAL;
  void DiscardUnknownFields();
  int GetCachedSize() const PROTOBUF_FINAL { return _cached_size_; }
  private:
  void SharedCtor();
  void SharedDtor();
  void SetCachedSize(int size) const;
  void InternalSwap(ForeignMessageLite* other);
  private:
  inline ::google::protobuf::Arena* GetArenaNoVirtual() const {
    return NULL;
  }
  inline void* MaybeArenaPtr() const {
    return NULL;
  }
  public:

  ::std::string GetTypeName() const PROTOBUF_FINAL;

  // nested types ----------------------------------------------------

  // accessors -------------------------------------------------------

  // optional int32 c = 1;
  bool has_c() const;
  void clear_c();
  static const int kCFieldNumber = 1;
  ::google::protobuf::int32 c() const;
  void set_c(::google::protobuf::int32 value);

  // @@protoc_insertion_point(class_scope:protobuf_unittest_no_arena.ForeignMessageLite)
 private:
  void set_has_c();
  void clear_has_c();

  ::google::protobuf::internal::InternalMetadataWithArenaLite _internal_metadata_;
  ::google::protobuf::internal::HasBits<1> _has_bits_;
  mutable int _cached_size_;
  ::google::protobuf::int32 c_;
  friend struct protobuf_google_2fprotobuf_2funittest_5fno_5farena_5flite_2eproto::TableStruct;
};
// ===================================================================


// ===================================================================

#if !PROTOBUF_INLINE_NOT_IN_HEADERS
#ifdef __GNUC__
  #pragma GCC diagnostic push
  #pragma GCC diagnostic ignored "-Wstrict-aliasing"
#endif  // __GNUC__
// ForeignMessageLite

// optional int32 c = 1;
inline bool ForeignMessageLite::has_c() const {
  return (_has_bits_[0] & 0x00000001u) != 0;
}
inline void ForeignMessageLite::set_has_c() {
  _has_bits_[0] |= 0x00000001u;
}
inline void ForeignMessageLite::clear_has_c() {
  _has_bits_[0] &= ~0x00000001u;
}
inline void ForeignMessageLite::clear_c() {
  c_ = 0;
  clear_has_c();
}
inline ::google::protobuf::int32 ForeignMessageLite::c() const {
  // @@protoc_insertion_point(field_get:protobuf_unittest_no_arena.ForeignMessageLite.c)
  return c_;
}
inline void ForeignMessageLite::set_c(::google::protobuf::int32 value) {
  set_has_c();
  c_ = value;
  // @@protoc_insertion_point(field_set:protobuf_unittest_no_arena.ForeignMessageLite.c)
}

#ifdef __GNUC__
  #pragma GCC diagnostic pop
#endif  // __GNUC__
#endif  // !PROTOBUF_INLINE_NOT_IN_HEADERS

// @@protoc_insertion_point(namespace_scope)


}  // namespace protobuf_unittest_no_arena

// @@protoc_insertion_point(global_scope)

#endif  // PROTOBUF_google_2fprotobuf_2funittest_5fno_5farena_5flite_2eproto__INCLUDED
