// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/protobuf/unittest_mset_wire_format.proto

#define INTERNAL_SUPPRESS_PROTOBUF_FIELD_DEPRECATION
#include <google/protobuf/unittest_mset_wire_format.pb.h>

#include <algorithm>

#include <google/protobuf/stubs/common.h>
#include <google/protobuf/stubs/port.h>
#include <google/protobuf/stubs/once.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/wire_format_lite_inl.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/generated_message_reflection.h>
#include <google/protobuf/reflection_ops.h>
#include <google/protobuf/wire_format.h>
// @@protoc_insertion_point(includes)

namespace proto2_wireformat_unittest {
class TestMessageSetDefaultTypeInternal {
public:
 ::google::protobuf::internal::ExplicitlyConstructed<TestMessageSet>
     _instance;
} _TestMessageSet_default_instance_;
class TestMessageSetWireFormatContainerDefaultTypeInternal {
public:
 ::google::protobuf::internal::ExplicitlyConstructed<TestMessageSetWireFormatContainer>
     _instance;
} _TestMessageSetWireFormatContainer_default_instance_;

namespace protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto {


namespace {

::google::protobuf::Metadata file_level_metadata[2];

}  // namespace

PROTOBUF_CONSTEXPR_VAR ::google::protobuf::internal::ParseTableField
    const TableStruct::entries[] GOOGLE_ATTRIBUTE_SECTION_VARIABLE(protodesc_cold) = {
  {0, 0, 0, ::google::protobuf::internal::kInvalidMask, 0, 0},
};

PROTOBUF_CONSTEXPR_VAR ::google::protobuf::internal::AuxillaryParseTableField
    const TableStruct::aux[] GOOGLE_ATTRIBUTE_SECTION_VARIABLE(protodesc_cold) = {
  ::google::protobuf::internal::AuxillaryParseTableField(),
};
PROTOBUF_CONSTEXPR_VAR ::google::protobuf::internal::ParseTable const
    TableStruct::schema[] GOOGLE_ATTRIBUTE_SECTION_VARIABLE(protodesc_cold) = {
  { NULL, NULL, 0, -1, -1, -1, -1, NULL, false },
  { NULL, NULL, 0, -1, -1, -1, -1, NULL, false },
};

const ::google::protobuf::uint32 TableStruct::offsets[] GOOGLE_ATTRIBUTE_SECTION_VARIABLE(protodesc_cold) = {
  GOOGLE_PROTOBUF_GENERATED_MESSAGE_FIELD_OFFSET(TestMessageSet, _has_bits_),
  GOOGLE_PROTOBUF_GENERATED_MESSAGE_FIELD_OFFSET(TestMessageSet, _internal_metadata_),
  GOOGLE_PROTOBUF_GENERATED_MESSAGE_FIELD_OFFSET(TestMessageSet, _extensions_),
  ~0u,  // no _oneof_case_
  ~0u,  // no _weak_field_map_
  GOOGLE_PROTOBUF_GENERATED_MESSAGE_FIELD_OFFSET(TestMessageSetWireFormatContainer, _has_bits_),
  GOOGLE_PROTOBUF_GENERATED_MESSAGE_FIELD_OFFSET(TestMessageSetWireFormatContainer, _internal_metadata_),
  ~0u,  // no _extensions_
  ~0u,  // no _oneof_case_
  ~0u,  // no _weak_field_map_
  GOOGLE_PROTOBUF_GENERATED_MESSAGE_FIELD_OFFSET(TestMessageSetWireFormatContainer, message_set_),
  0,
};
static const ::google::protobuf::internal::MigrationSchema schemas[] GOOGLE_ATTRIBUTE_SECTION_VARIABLE(protodesc_cold) = {
  { 0, 5, sizeof(TestMessageSet)},
  { 5, 11, sizeof(TestMessageSetWireFormatContainer)},
};

static ::google::protobuf::Message const * const file_default_instances[] = {
  reinterpret_cast<const ::google::protobuf::Message*>(&_TestMessageSet_default_instance_),
  reinterpret_cast<const ::google::protobuf::Message*>(&_TestMessageSetWireFormatContainer_default_instance_),
};

namespace {

void protobuf_AssignDescriptors() {
  AddDescriptors();
  ::google::protobuf::MessageFactory* factory = NULL;
  AssignDescriptors(
      "google/protobuf/unittest_mset_wire_format.proto", schemas, file_default_instances, TableStruct::offsets, factory,
      file_level_metadata, NULL, NULL);
}

void protobuf_AssignDescriptorsOnce() {
  static GOOGLE_PROTOBUF_DECLARE_ONCE(once);
  ::google::protobuf::GoogleOnceInit(&once, &protobuf_AssignDescriptors);
}

void protobuf_RegisterTypes(const ::std::string&) GOOGLE_ATTRIBUTE_COLD;
void protobuf_RegisterTypes(const ::std::string&) {
  protobuf_AssignDescriptorsOnce();
  ::google::protobuf::internal::RegisterAllTypes(file_level_metadata, 2);
}

}  // namespace
void TableStruct::InitDefaultsImpl() {
  GOOGLE_PROTOBUF_VERIFY_VERSION;

  ::google::protobuf::internal::InitProtobufDefaults();
  _TestMessageSet_default_instance_._instance.DefaultConstruct();
  ::google::protobuf::internal::OnShutdownDestroyMessage(
      &_TestMessageSet_default_instance_);_TestMessageSetWireFormatContainer_default_instance_._instance.DefaultConstruct();
  ::google::protobuf::internal::OnShutdownDestroyMessage(
      &_TestMessageSetWireFormatContainer_default_instance_);_TestMessageSetWireFormatContainer_default_instance_._instance.get_mutable()->message_set_ = const_cast< ::proto2_wireformat_unittest::TestMessageSet*>(
      ::proto2_wireformat_unittest::TestMessageSet::internal_default_instance());
}

void InitDefaults() {
  static GOOGLE_PROTOBUF_DECLARE_ONCE(once);
  ::google::protobuf::GoogleOnceInit(&once, &TableStruct::InitDefaultsImpl);
}
namespace {
void AddDescriptorsImpl() {
  InitDefaults();
  static const char descriptor[] GOOGLE_ATTRIBUTE_SECTION_VARIABLE(protodesc_cold) = {
      "\n/google/protobuf/unittest_mset_wire_for"
      "mat.proto\022\032proto2_wireformat_unittest\"\036\n"
      "\016TestMessageSet*\010\010\004\020\377\377\377\377\007:\002\010\001\"d\n!TestMes"
      "sageSetWireFormatContainer\022\?\n\013message_se"
      "t\030\001 \001(\0132*.proto2_wireformat_unittest.Tes"
      "tMessageSetB)H\001\370\001\001\252\002!Google.ProtocolBuff"
      "ers.TestProtos"
  };
  ::google::protobuf::DescriptorPool::InternalAddGeneratedFile(
      descriptor, 254);
  ::google::protobuf::MessageFactory::InternalRegisterGeneratedFile(
    "google/protobuf/unittest_mset_wire_format.proto", &protobuf_RegisterTypes);
}
} // anonymous namespace

void AddDescriptors() {
  static GOOGLE_PROTOBUF_DECLARE_ONCE(once);
  ::google::protobuf::GoogleOnceInit(&once, &AddDescriptorsImpl);
}
// Force AddDescriptors() to be called at dynamic initialization time.
struct StaticDescriptorInitializer {
  StaticDescriptorInitializer() {
    AddDescriptors();
  }
} static_descriptor_initializer;

}  // namespace protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto


// ===================================================================

#if !defined(_MSC_VER) || _MSC_VER >= 1900
#endif  // !defined(_MSC_VER) || _MSC_VER >= 1900

TestMessageSet::TestMessageSet()
  : ::google::protobuf::Message(), _internal_metadata_(NULL) {
  if (GOOGLE_PREDICT_TRUE(this != internal_default_instance())) {
    protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::InitDefaults();
  }
  SharedCtor();
  // @@protoc_insertion_point(constructor:proto2_wireformat_unittest.TestMessageSet)
}
TestMessageSet::TestMessageSet(::google::protobuf::Arena* arena)
  : ::google::protobuf::Message(),
  _extensions_(arena),
  _internal_metadata_(arena) {
  protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::InitDefaults();
  SharedCtor();
  RegisterArenaDtor(arena);
  // @@protoc_insertion_point(arena_constructor:proto2_wireformat_unittest.TestMessageSet)
}
TestMessageSet::TestMessageSet(const TestMessageSet& from)
  : ::google::protobuf::Message(),
      _internal_metadata_(NULL),
      _has_bits_(from._has_bits_),
      _cached_size_(0) {
  _internal_metadata_.MergeFrom(from._internal_metadata_);
  _extensions_.MergeFrom(from._extensions_);
  // @@protoc_insertion_point(copy_constructor:proto2_wireformat_unittest.TestMessageSet)
}

void TestMessageSet::SharedCtor() {
  _cached_size_ = 0;
}

TestMessageSet::~TestMessageSet() {
  // @@protoc_insertion_point(destructor:proto2_wireformat_unittest.TestMessageSet)
  SharedDtor();
}

void TestMessageSet::SharedDtor() {
  ::google::protobuf::Arena* arena = GetArenaNoVirtual();
  GOOGLE_DCHECK(arena == NULL);
  if (arena != NULL) {
    return;
  }

}

void TestMessageSet::ArenaDtor(void* object) {
  TestMessageSet* _this = reinterpret_cast< TestMessageSet* >(object);
  (void)_this;
}
void TestMessageSet::RegisterArenaDtor(::google::protobuf::Arena* arena) {
}
void TestMessageSet::SetCachedSize(int size) const {
  GOOGLE_SAFE_CONCURRENT_WRITES_BEGIN();
  _cached_size_ = size;
  GOOGLE_SAFE_CONCURRENT_WRITES_END();
}
const ::google::protobuf::Descriptor* TestMessageSet::descriptor() {
  protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::protobuf_AssignDescriptorsOnce();
  return protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::file_level_metadata[kIndexInFileMessages].descriptor;
}

const TestMessageSet& TestMessageSet::default_instance() {
  protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::InitDefaults();
  return *internal_default_instance();
}

TestMessageSet* TestMessageSet::New(::google::protobuf::Arena* arena) const {
  return ::google::protobuf::Arena::CreateMessage<TestMessageSet>(arena);
}

void TestMessageSet::Clear() {
// @@protoc_insertion_point(message_clear_start:proto2_wireformat_unittest.TestMessageSet)
  ::google::protobuf::uint32 cached_has_bits = 0;
  // Prevent compiler warnings about cached_has_bits being unused
  (void) cached_has_bits;

  _extensions_.Clear();
  _has_bits_.Clear();
  _internal_metadata_.Clear();
}

bool TestMessageSet::MergePartialFromCodedStream(
    ::google::protobuf::io::CodedInputStream* input) {
  return _extensions_.ParseMessageSet(input,
      internal_default_instance(), _internal_metadata_.mutable_unknown_fields());
}

void TestMessageSet::SerializeWithCachedSizes(
    ::google::protobuf::io::CodedOutputStream* output) const {
  _extensions_.SerializeMessageSetWithCachedSizes(output);
  ::google::protobuf::internal::WireFormat::SerializeUnknownMessageSetItems(
      _internal_metadata_.unknown_fields(), output);
}

::google::protobuf::uint8* TestMessageSet::InternalSerializeWithCachedSizesToArray(
    bool deterministic, ::google::protobuf::uint8* target) const {
  target = _extensions_.InternalSerializeMessageSetWithCachedSizesToArray(
               deterministic, target);
  target = ::google::protobuf::internal::WireFormat::
             SerializeUnknownMessageSetItemsToArray(
               _internal_metadata_.unknown_fields(), target);
  return target;
}

size_t TestMessageSet::ByteSizeLong() const {
// @@protoc_insertion_point(message_set_byte_size_start:proto2_wireformat_unittest.TestMessageSet)
  size_t total_size = _extensions_.MessageSetByteSize();
  if (_internal_metadata_.have_unknown_fields()) {
    total_size += ::google::protobuf::internal::WireFormat::
        ComputeUnknownMessageSetItemsSize(_internal_metadata_.unknown_fields());
  }
  int cached_size = ::google::protobuf::internal::ToCachedSize(total_size);
  GOOGLE_SAFE_CONCURRENT_WRITES_BEGIN();
  _cached_size_ = cached_size;
  GOOGLE_SAFE_CONCURRENT_WRITES_END();
  return total_size;
}

void TestMessageSet::MergeFrom(const ::google::protobuf::Message& from) {
// @@protoc_insertion_point(generalized_merge_from_start:proto2_wireformat_unittest.TestMessageSet)
  GOOGLE_DCHECK_NE(&from, this);
  const TestMessageSet* source =
      ::google::protobuf::internal::DynamicCastToGenerated<const TestMessageSet>(
          &from);
  if (source == NULL) {
  // @@protoc_insertion_point(generalized_merge_from_cast_fail:proto2_wireformat_unittest.TestMessageSet)
    ::google::protobuf::internal::ReflectionOps::Merge(from, this);
  } else {
  // @@protoc_insertion_point(generalized_merge_from_cast_success:proto2_wireformat_unittest.TestMessageSet)
    MergeFrom(*source);
  }
}

void TestMessageSet::MergeFrom(const TestMessageSet& from) {
// @@protoc_insertion_point(class_specific_merge_from_start:proto2_wireformat_unittest.TestMessageSet)
  GOOGLE_DCHECK_NE(&from, this);
  _extensions_.MergeFrom(from._extensions_);
  _internal_metadata_.MergeFrom(from._internal_metadata_);
  ::google::protobuf::uint32 cached_has_bits = 0;
  (void) cached_has_bits;

}

void TestMessageSet::CopyFrom(const ::google::protobuf::Message& from) {
// @@protoc_insertion_point(generalized_copy_from_start:proto2_wireformat_unittest.TestMessageSet)
  if (&from == this) return;
  Clear();
  MergeFrom(from);
}

void TestMessageSet::CopyFrom(const TestMessageSet& from) {
// @@protoc_insertion_point(class_specific_copy_from_start:proto2_wireformat_unittest.TestMessageSet)
  if (&from == this) return;
  Clear();
  MergeFrom(from);
}

bool TestMessageSet::IsInitialized() const {
  if (!_extensions_.IsInitialized()) {
    return false;
  }

  return true;
}

void TestMessageSet::Swap(TestMessageSet* other) {
  if (other == this) return;
  if (GetArenaNoVirtual() == other->GetArenaNoVirtual()) {
    InternalSwap(other);
  } else {
    TestMessageSet* temp = New(GetArenaNoVirtual());
    temp->MergeFrom(*other);
    other->CopyFrom(*this);
    InternalSwap(temp);
    if (GetArenaNoVirtual() == NULL) {
      delete temp;
    }
  }
}
void TestMessageSet::UnsafeArenaSwap(TestMessageSet* other) {
  if (other == this) return;
  GOOGLE_DCHECK(GetArenaNoVirtual() == other->GetArenaNoVirtual());
  InternalSwap(other);
}
void TestMessageSet::InternalSwap(TestMessageSet* other) {
  using std::swap;
  swap(_has_bits_[0], other->_has_bits_[0]);
  _internal_metadata_.Swap(&other->_internal_metadata_);
  swap(_cached_size_, other->_cached_size_);
  _extensions_.Swap(&other->_extensions_);
}

::google::protobuf::Metadata TestMessageSet::GetMetadata() const {
  protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::protobuf_AssignDescriptorsOnce();
  return protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::file_level_metadata[kIndexInFileMessages];
}

#if PROTOBUF_INLINE_NOT_IN_HEADERS
// TestMessageSet

#endif  // PROTOBUF_INLINE_NOT_IN_HEADERS

// ===================================================================

void TestMessageSetWireFormatContainer::_slow_mutable_message_set() {
  message_set_ = ::google::protobuf::Arena::CreateMessage< ::proto2_wireformat_unittest::TestMessageSet >(
      GetArenaNoVirtual());
}
::proto2_wireformat_unittest::TestMessageSet* TestMessageSetWireFormatContainer::_slow_release_message_set() {
  if (message_set_ == NULL) {
    return NULL;
  } else {
    ::proto2_wireformat_unittest::TestMessageSet* temp = new ::proto2_wireformat_unittest::TestMessageSet(*message_set_);
    message_set_ = NULL;
    return temp;
  }
}
::proto2_wireformat_unittest::TestMessageSet* TestMessageSetWireFormatContainer::unsafe_arena_release_message_set() {
  // @@protoc_insertion_point(field_unsafe_arena_release:proto2_wireformat_unittest.TestMessageSetWireFormatContainer.message_set)
  clear_has_message_set();
  ::proto2_wireformat_unittest::TestMessageSet* temp = message_set_;
  message_set_ = NULL;
  return temp;
}
void TestMessageSetWireFormatContainer::_slow_set_allocated_message_set(
    ::google::protobuf::Arena* message_arena, ::proto2_wireformat_unittest::TestMessageSet** message_set) {
    if (message_arena != NULL && 
        ::google::protobuf::Arena::GetArena(*message_set) == NULL) {
      message_arena->Own(*message_set);
    } else if (message_arena !=
               ::google::protobuf::Arena::GetArena(*message_set)) {
      ::proto2_wireformat_unittest::TestMessageSet* new_message_set = 
            ::google::protobuf::Arena::CreateMessage< ::proto2_wireformat_unittest::TestMessageSet >(
            message_arena);
      new_message_set->CopyFrom(**message_set);
      *message_set = new_message_set;
    }
}
void TestMessageSetWireFormatContainer::unsafe_arena_set_allocated_message_set(
    ::proto2_wireformat_unittest::TestMessageSet* message_set) {
  if (GetArenaNoVirtual() == NULL) {
    delete message_set_;
  }
  message_set_ = message_set;
  if (message_set) {
    set_has_message_set();
  } else {
    clear_has_message_set();
  }
  // @@protoc_insertion_point(field_unsafe_arena_set_allocated:proto2_wireformat_unittest.TestMessageSetWireFormatContainer.message_set)
}
#if !defined(_MSC_VER) || _MSC_VER >= 1900
const int TestMessageSetWireFormatContainer::kMessageSetFieldNumber;
#endif  // !defined(_MSC_VER) || _MSC_VER >= 1900

TestMessageSetWireFormatContainer::TestMessageSetWireFormatContainer()
  : ::google::protobuf::Message(), _internal_metadata_(NULL) {
  if (GOOGLE_PREDICT_TRUE(this != internal_default_instance())) {
    protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::InitDefaults();
  }
  SharedCtor();
  // @@protoc_insertion_point(constructor:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
}
TestMessageSetWireFormatContainer::TestMessageSetWireFormatContainer(::google::protobuf::Arena* arena)
  : ::google::protobuf::Message(),
  _internal_metadata_(arena) {
  protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::InitDefaults();
  SharedCtor();
  RegisterArenaDtor(arena);
  // @@protoc_insertion_point(arena_constructor:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
}
TestMessageSetWireFormatContainer::TestMessageSetWireFormatContainer(const TestMessageSetWireFormatContainer& from)
  : ::google::protobuf::Message(),
      _internal_metadata_(NULL),
      _has_bits_(from._has_bits_),
      _cached_size_(0) {
  _internal_metadata_.MergeFrom(from._internal_metadata_);
  if (from.has_message_set()) {
    message_set_ = new ::proto2_wireformat_unittest::TestMessageSet(*from.message_set_);
  } else {
    message_set_ = NULL;
  }
  // @@protoc_insertion_point(copy_constructor:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
}

void TestMessageSetWireFormatContainer::SharedCtor() {
  _cached_size_ = 0;
  message_set_ = NULL;
}

TestMessageSetWireFormatContainer::~TestMessageSetWireFormatContainer() {
  // @@protoc_insertion_point(destructor:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  SharedDtor();
}

void TestMessageSetWireFormatContainer::SharedDtor() {
  ::google::protobuf::Arena* arena = GetArenaNoVirtual();
  GOOGLE_DCHECK(arena == NULL);
  if (arena != NULL) {
    return;
  }

  if (this != internal_default_instance()) delete message_set_;
}

void TestMessageSetWireFormatContainer::ArenaDtor(void* object) {
  TestMessageSetWireFormatContainer* _this = reinterpret_cast< TestMessageSetWireFormatContainer* >(object);
  (void)_this;
}
void TestMessageSetWireFormatContainer::RegisterArenaDtor(::google::protobuf::Arena* arena) {
}
void TestMessageSetWireFormatContainer::SetCachedSize(int size) const {
  GOOGLE_SAFE_CONCURRENT_WRITES_BEGIN();
  _cached_size_ = size;
  GOOGLE_SAFE_CONCURRENT_WRITES_END();
}
const ::google::protobuf::Descriptor* TestMessageSetWireFormatContainer::descriptor() {
  protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::protobuf_AssignDescriptorsOnce();
  return protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::file_level_metadata[kIndexInFileMessages].descriptor;
}

const TestMessageSetWireFormatContainer& TestMessageSetWireFormatContainer::default_instance() {
  protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::InitDefaults();
  return *internal_default_instance();
}

TestMessageSetWireFormatContainer* TestMessageSetWireFormatContainer::New(::google::protobuf::Arena* arena) const {
  return ::google::protobuf::Arena::CreateMessage<TestMessageSetWireFormatContainer>(arena);
}

void TestMessageSetWireFormatContainer::Clear() {
// @@protoc_insertion_point(message_clear_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  ::google::protobuf::uint32 cached_has_bits = 0;
  // Prevent compiler warnings about cached_has_bits being unused
  (void) cached_has_bits;

  if (has_message_set()) {
    GOOGLE_DCHECK(message_set_ != NULL);
    message_set_->::proto2_wireformat_unittest::TestMessageSet::Clear();
  }
  _has_bits_.Clear();
  _internal_metadata_.Clear();
}

bool TestMessageSetWireFormatContainer::MergePartialFromCodedStream(
    ::google::protobuf::io::CodedInputStream* input) {
#define DO_(EXPRESSION) if (!GOOGLE_PREDICT_TRUE(EXPRESSION)) goto failure
  ::google::protobuf::uint32 tag;
  // @@protoc_insertion_point(parse_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  for (;;) {
    ::std::pair< ::google::protobuf::uint32, bool> p = input->ReadTagWithCutoffNoLastTag(127u);
    tag = p.first;
    if (!p.second) goto handle_unusual;
    switch (::google::protobuf::internal::WireFormatLite::GetTagFieldNumber(tag)) {
      // optional .proto2_wireformat_unittest.TestMessageSet message_set = 1;
      case 1: {
        if (static_cast< ::google::protobuf::uint8>(tag) ==
            static_cast< ::google::protobuf::uint8>(10u /* 10 & 0xFF */)) {
          DO_(::google::protobuf::internal::WireFormatLite::ReadMessageNoVirtual(
               input, mutable_message_set()));
        } else {
          goto handle_unusual;
        }
        break;
      }

      default: {
      handle_unusual:
        if (tag == 0) {
          goto success;
        }
        DO_(::google::protobuf::internal::WireFormat::SkipField(
              input, tag, _internal_metadata_.mutable_unknown_fields()));
        break;
      }
    }
  }
success:
  // @@protoc_insertion_point(parse_success:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  return true;
failure:
  // @@protoc_insertion_point(parse_failure:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  return false;
#undef DO_
}

void TestMessageSetWireFormatContainer::SerializeWithCachedSizes(
    ::google::protobuf::io::CodedOutputStream* output) const {
  // @@protoc_insertion_point(serialize_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  ::google::protobuf::uint32 cached_has_bits = 0;
  (void) cached_has_bits;

  cached_has_bits = _has_bits_[0];
  // optional .proto2_wireformat_unittest.TestMessageSet message_set = 1;
  if (cached_has_bits & 0x00000001u) {
    ::google::protobuf::internal::WireFormatLite::WriteMessageMaybeToArray(
      1, *this->message_set_, output);
  }

  if (_internal_metadata_.have_unknown_fields()) {
    ::google::protobuf::internal::WireFormat::SerializeUnknownFields(
        _internal_metadata_.unknown_fields(), output);
  }
  // @@protoc_insertion_point(serialize_end:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
}

::google::protobuf::uint8* TestMessageSetWireFormatContainer::InternalSerializeWithCachedSizesToArray(
    bool deterministic, ::google::protobuf::uint8* target) const {
  (void)deterministic; // Unused
  // @@protoc_insertion_point(serialize_to_array_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  ::google::protobuf::uint32 cached_has_bits = 0;
  (void) cached_has_bits;

  cached_has_bits = _has_bits_[0];
  // optional .proto2_wireformat_unittest.TestMessageSet message_set = 1;
  if (cached_has_bits & 0x00000001u) {
    target = ::google::protobuf::internal::WireFormatLite::
      InternalWriteMessageNoVirtualToArray(
        1, *this->message_set_, deterministic, target);
  }

  if (_internal_metadata_.have_unknown_fields()) {
    target = ::google::protobuf::internal::WireFormat::SerializeUnknownFieldsToArray(
        _internal_metadata_.unknown_fields(), target);
  }
  // @@protoc_insertion_point(serialize_to_array_end:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  return target;
}

size_t TestMessageSetWireFormatContainer::ByteSizeLong() const {
// @@protoc_insertion_point(message_byte_size_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  size_t total_size = 0;

  if (_internal_metadata_.have_unknown_fields()) {
    total_size +=
      ::google::protobuf::internal::WireFormat::ComputeUnknownFieldsSize(
        _internal_metadata_.unknown_fields());
  }
  // optional .proto2_wireformat_unittest.TestMessageSet message_set = 1;
  if (has_message_set()) {
    total_size += 1 +
      ::google::protobuf::internal::WireFormatLite::MessageSizeNoVirtual(
        *this->message_set_);
  }

  int cached_size = ::google::protobuf::internal::ToCachedSize(total_size);
  GOOGLE_SAFE_CONCURRENT_WRITES_BEGIN();
  _cached_size_ = cached_size;
  GOOGLE_SAFE_CONCURRENT_WRITES_END();
  return total_size;
}

void TestMessageSetWireFormatContainer::MergeFrom(const ::google::protobuf::Message& from) {
// @@protoc_insertion_point(generalized_merge_from_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  GOOGLE_DCHECK_NE(&from, this);
  const TestMessageSetWireFormatContainer* source =
      ::google::protobuf::internal::DynamicCastToGenerated<const TestMessageSetWireFormatContainer>(
          &from);
  if (source == NULL) {
  // @@protoc_insertion_point(generalized_merge_from_cast_fail:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
    ::google::protobuf::internal::ReflectionOps::Merge(from, this);
  } else {
  // @@protoc_insertion_point(generalized_merge_from_cast_success:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
    MergeFrom(*source);
  }
}

void TestMessageSetWireFormatContainer::MergeFrom(const TestMessageSetWireFormatContainer& from) {
// @@protoc_insertion_point(class_specific_merge_from_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  GOOGLE_DCHECK_NE(&from, this);
  _internal_metadata_.MergeFrom(from._internal_metadata_);
  ::google::protobuf::uint32 cached_has_bits = 0;
  (void) cached_has_bits;

  if (from.has_message_set()) {
    mutable_message_set()->::proto2_wireformat_unittest::TestMessageSet::MergeFrom(from.message_set());
  }
}

void TestMessageSetWireFormatContainer::CopyFrom(const ::google::protobuf::Message& from) {
// @@protoc_insertion_point(generalized_copy_from_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  if (&from == this) return;
  Clear();
  MergeFrom(from);
}

void TestMessageSetWireFormatContainer::CopyFrom(const TestMessageSetWireFormatContainer& from) {
// @@protoc_insertion_point(class_specific_copy_from_start:proto2_wireformat_unittest.TestMessageSetWireFormatContainer)
  if (&from == this) return;
  Clear();
  MergeFrom(from);
}

bool TestMessageSetWireFormatContainer::IsInitialized() const {
  if (has_message_set()) {
    if (!this->message_set_->IsInitialized()) return false;
  }
  return true;
}

void TestMessageSetWireFormatContainer::Swap(TestMessageSetWireFormatContainer* other) {
  if (other == this) return;
  if (GetArenaNoVirtual() == other->GetArenaNoVirtual()) {
    InternalSwap(other);
  } else {
    TestMessageSetWireFormatContainer* temp = New(GetArenaNoVirtual());
    temp->MergeFrom(*other);
    other->CopyFrom(*this);
    InternalSwap(temp);
    if (GetArenaNoVirtual() == NULL) {
      delete temp;
    }
  }
}
void TestMessageSetWireFormatContainer::UnsafeArenaSwap(TestMessageSetWireFormatContainer* other) {
  if (other == this) return;
  GOOGLE_DCHECK(GetArenaNoVirtual() == other->GetArenaNoVirtual());
  InternalSwap(other);
}
void TestMessageSetWireFormatContainer::InternalSwap(TestMessageSetWireFormatContainer* other) {
  using std::swap;
  swap(message_set_, other->message_set_);
  swap(_has_bits_[0], other->_has_bits_[0]);
  _internal_metadata_.Swap(&other->_internal_metadata_);
  swap(_cached_size_, other->_cached_size_);
}

::google::protobuf::Metadata TestMessageSetWireFormatContainer::GetMetadata() const {
  protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::protobuf_AssignDescriptorsOnce();
  return protobuf_google_2fprotobuf_2funittest_5fmset_5fwire_5fformat_2eproto::file_level_metadata[kIndexInFileMessages];
}

#if PROTOBUF_INLINE_NOT_IN_HEADERS
// TestMessageSetWireFormatContainer

// optional .proto2_wireformat_unittest.TestMessageSet message_set = 1;
bool TestMessageSetWireFormatContainer::has_message_set() const {
  return (_has_bits_[0] & 0x00000001u) != 0;
}
void TestMessageSetWireFormatContainer::set_has_message_set() {
  _has_bits_[0] |= 0x00000001u;
}
void TestMessageSetWireFormatContainer::clear_has_message_set() {
  _has_bits_[0] &= ~0x00000001u;
}
void TestMessageSetWireFormatContainer::clear_message_set() {
  if (message_set_ != NULL) message_set_->::proto2_wireformat_unittest::TestMessageSet::Clear();
  clear_has_message_set();
}
const ::proto2_wireformat_unittest::TestMessageSet& TestMessageSetWireFormatContainer::message_set() const {
  const ::proto2_wireformat_unittest::TestMessageSet* p = message_set_;
  // @@protoc_insertion_point(field_get:proto2_wireformat_unittest.TestMessageSetWireFormatContainer.message_set)
  return p != NULL ? *p : *reinterpret_cast<const ::proto2_wireformat_unittest::TestMessageSet*>(
      &::proto2_wireformat_unittest::_TestMessageSet_default_instance_);
}
::proto2_wireformat_unittest::TestMessageSet* TestMessageSetWireFormatContainer::mutable_message_set() {
  set_has_message_set();
  if (message_set_ == NULL) {
    _slow_mutable_message_set();
  }
  // @@protoc_insertion_point(field_mutable:proto2_wireformat_unittest.TestMessageSetWireFormatContainer.message_set)
  return message_set_;
}
::proto2_wireformat_unittest::TestMessageSet* TestMessageSetWireFormatContainer::release_message_set() {
  // @@protoc_insertion_point(field_release:proto2_wireformat_unittest.TestMessageSetWireFormatContainer.message_set)
  clear_has_message_set();
  if (GetArenaNoVirtual() != NULL) {
    return _slow_release_message_set();
  } else {
    ::proto2_wireformat_unittest::TestMessageSet* temp = message_set_;
    message_set_ = NULL;
    return temp;
  }
}
 void TestMessageSetWireFormatContainer::set_allocated_message_set(::proto2_wireformat_unittest::TestMessageSet* message_set) {
  ::google::protobuf::Arena* message_arena = GetArenaNoVirtual();
  if (message_arena == NULL) {
    delete message_set_;
  }
  if (message_set != NULL) {
    _slow_set_allocated_message_set(message_arena, &message_set);
  }
  message_set_ = message_set;
  if (message_set) {
    set_has_message_set();
  } else {
    clear_has_message_set();
  }
  // @@protoc_insertion_point(field_set_allocated:proto2_wireformat_unittest.TestMessageSetWireFormatContainer.message_set)
}

#endif  // PROTOBUF_INLINE_NOT_IN_HEADERS

// @@protoc_insertion_point(namespace_scope)

}  // namespace proto2_wireformat_unittest

// @@protoc_insertion_point(global_scope)
