#include "include/Logger.h"
#include "c_SDDRRadio.h"

static struct jThingsCache {
    jfieldID fidc_RadioPtr;
    jfieldID fidChangeEpoch;
    jfieldID fidDiscover;
    jfieldID fidc_EncounterMsgs;
    jfieldID fidc_DevAddrs;
    jclass clsNative;
    jclass clsArray;
    jclass clsAT;
    jclass clsRA;
    jmethodID constructorRA;
    jmethodID addArray;
} jni_cache;

SDDRRadio* setupRadio(Config config) {
    EbNHystPolicy hystPolicy(
            config.hyst.scheme,
            config.hyst.minStartTime,
            config.hyst.maxStartTime,
            config.hyst.startSeen,
            config.hyst.endTime,
            config.hyst.rssiThreshold);
    return new SDDRRadio(
           config.radio.keySize,
           config.radio.confirm,
           config.radio.memory,
           config.radio.retroactive,
           0,
           hystPolicy,
           config.reporting.rssiInterval);
}

/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_Native
 * Method:    c_mallocRadio
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1mallocRadio
        (JNIEnv *env, jobject obj) {
    // initialize the logger
    logger->setLogcatEnabled(false);
    logger->setLogcatEnabled(true);
    LOG_P("SDDR_c", "logger enabled"); 
 
    LOG_P("SDDR_c", "calling malloc Radio"); 

    // XXX right now we're not customizing anything
    SDDRRadio* radioPtr = setupRadio(configDefaults);
    LOG_P("c_encounters_SDDR", "-- set up radio pointer %p", radioPtr);
   
    // initialize all the JNI IDs we will need
    jni_cache.clsRA = (jclass) env->NewGlobalRef(env->FindClass("org/mpisws/sddrservice/encounters/SDDR_Core$RadioAction"));
    jni_cache.clsAT = (jclass) env->NewGlobalRef(env->FindClass("org/mpisws/sddrservice/encounters/SDDR_Core$RadioAction$actionType"));
    jni_cache.clsArray = (jclass) env->NewGlobalRef(env->FindClass("java/util/ArrayList"));
    jni_cache.clsNative = (jclass) env->NewGlobalRef(env->FindClass("org/mpisws/sddrservice/encounters/SDDR_Native"));
    if (!jni_cache.clsAT || !jni_cache.clsRA || !jni_cache.clsArray) {
        LOG_P("c_encounters_SDDR", "Class not found");
        assert(0);
    }
    jni_cache.fidChangeEpoch = env->GetStaticFieldID(jni_cache.clsAT, "ChangeEpoch", "Lorg/mpisws/sddrservice/encounters/SDDR_Core$RadioAction$actionType;");
    jni_cache.fidDiscover = env->GetStaticFieldID(jni_cache.clsAT, "Discover", "Lorg/mpisws/sddrservice/encounters/SDDR_Core$RadioAction$actionType;");
    jni_cache.fidc_EncounterMsgs = env->GetStaticFieldID(jni_cache.clsNative, "c_EncounterMsgs", "Ljava/util/ArrayList;");
    jni_cache.fidc_DevAddrs = env->GetStaticFieldID(jni_cache.clsNative, "c_DevAddrs", "Ljava/util/ArrayList;");

    jni_cache.constructorRA = env->GetMethodID(jni_cache.clsRA, "<init>", 
                "(Lorg/mpisws/sddrservice/encounters/SDDR_Core$RadioAction$actionType;J)V");
    jni_cache.addArray = env->GetMethodID(jni_cache.clsArray, "add", "(Ljava/lang/Object;)Z");
    if (!jni_cache.fidChangeEpoch || !jni_cache.fidDiscover || !jni_cache.addArray ||
            !jni_cache.fidc_EncounterMsgs) {
        LOG_P("c_encounters_SDDR", "Method or FieldID not found");
        assert(0);
    }

    // save the pointer as a Java long field so we can access this radio again
    jni_cache.fidc_RadioPtr = env->GetStaticFieldID(jni_cache.clsNative, "c_RadioPtr", "J");
    env->SetStaticLongField(jni_cache.clsNative, jni_cache.fidc_RadioPtr, (jlong)radioPtr); 
    return;
}

SDDRRadio* getRadioPtr(JNIEnv* env, jobject obj) {
    SDDRRadio* radioPtr = (SDDRRadio*)env->GetStaticLongField(jni_cache.clsNative, jni_cache.fidc_RadioPtr); 
    return radioPtr;
}

/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_Native
 * Method:    c_freeRadio
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1freeRadio
        (JNIEnv *env, jobject obj) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    delete radioPtr;
    env->DeleteGlobalRef(jni_cache.clsArray);
    env->DeleteGlobalRef(jni_cache.clsAT);
    env->DeleteGlobalRef(jni_cache.clsRA);
}

/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_Native
 * Method:    c_getNextRadioAction
 * Signature: ()L
 */
JNIEXPORT jobject JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1getNextRadioAction
  (JNIEnv *env, jobject obj) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);

    jobject action_type;
    SDDRRadio::ActionInfo ai = radioPtr->getNextAction();
    switch(ai.action) {
        case SDDRRadio::Action::ChangeEpoch:
        {
            LOG_P("c_encounters_SDDR", "got action ChangeEpoch");
            action_type = env->GetStaticObjectField(jni_cache.clsAT, jni_cache.fidChangeEpoch);
            break;
        }
        case SDDRRadio::Action::Discover:
        {
            LOG_P("c_encounters_SDDR", "got action Discover");
            action_type = env->GetStaticObjectField(jni_cache.clsAT, jni_cache.fidDiscover);
            break;
        }
    }

    jlong duration = (jlong) ai.timeUntil;
    return env->NewObject(jni_cache.clsRA, jni_cache.constructorRA, action_type, duration);
}

/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_Native
 * Method:    c_changeAndGetAdvert
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1changeAndGetAdvert
  (JNIEnv *env, jobject obj) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    
    char const* bytes = radioPtr->changeAndGetAdvert();
    jbyteArray arr = env->NewByteArray(ADVERT_LEN);
    env->SetByteArrayRegion(arr, 0, ADVERT_LEN, (jbyte*)bytes);
    return arr;
}

/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_Native
 * Method:    c_changeEpoch
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1changeEpoch
  (JNIEnv *env, jobject obj) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    radioPtr->changeEpoch();
}

/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_Native
 * Method:    c_getRandomAddr
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1getRandomAddr
  (JNIEnv *env, jobject obj) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);

    Address addr = radioPtr->getRandomAddr();
    uint8_t* bytes = addr.toByteArray();
    LOG_P("c_encounters_SDDR", "Got Addr %s with len %d", addr.toString().c_str(), addr.len());
    const jbyte* nativemsg = reinterpret_cast<const jbyte*>(bytes);
    jbyteArray jbytes = env->NewByteArray(ADDR_LEN);
    env->SetByteArrayRegion(jbytes, 0, ADDR_LEN, nativemsg);
    return jbytes;
}

/* From class org_mpisws_sddrservice_encounters_SDDR_1Native */
/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_1Native
 * Method:    c_processScanResult
 * Signature: ([BI[B)V
 */
JNIEXPORT void JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1processScanResult
  (JNIEnv *env, jobject obj, jbyteArray jaddr, jint jrssi, jbyteArray jadvert, jbyteArray jdevAddress){
    SDDRRadio* radioPtr = getRadioPtr(env, obj);

    int addr_len = env->GetArrayLength(jaddr);
    char* addr = new char[addr_len];
    env->GetByteArrayRegion(jaddr, 0, addr_len, reinterpret_cast<jbyte*>(addr));
    assert(addr_len / 8 == ADDR_LEN);
    Address myAddr(ADDR_LEN, (uint8_t*)addr);
    
    int advert_len = env->GetArrayLength(jadvert);
    char* advert = new char[advert_len];
    env->GetByteArrayRegion(jadvert, 0, advert_len, reinterpret_cast<jbyte*>(advert));

    int devAddrLen = env->GetArrayLength(jdevAddress);
    char* devaddrBytes = new char[devAddrLen];
    env->GetByteArrayRegion(jdevAddress, 0, devAddrLen, reinterpret_cast<jbyte*>(devaddrBytes));
    std::string devaddrStr(devaddrBytes, devAddrLen);
    radioPtr->processScanResponse(myAddr, (int)jrssi, (uint8_t*)advert, devaddrStr);
}

/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_1Native
 * Method:    c_preDiscovery
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1preDiscovery
  (JNIEnv *env, jobject obj) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    radioPtr->preDiscovery();
}

/*
 * Class:     org_mpisws_sddrservice_encounters_SDDR_1Native
 * Method:    c_postDiscovery
 * Signature: ()V
 */
 JNIEXPORT void JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1postDiscovery
  (JNIEnv *env, jobject obj) {
    LOG_P(TAG, "Starting post discovery jni code");
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    std::pair<
            std::vector<std::string>,
            std::vector<std::string>
        > msgs_addrs = radioPtr->postDiscoveryGetEncounters();

    jobject arraylistObjMsgs = env->GetStaticObjectField(jni_cache.clsNative, 
            jni_cache.fidc_EncounterMsgs); 
    jobject arraylistObjAddrs = env->GetStaticObjectField(jni_cache.clsNative, 
            jni_cache.fidc_DevAddrs); 
    jbyteArray bytes;
    for (std::string m : msgs_addrs.first) {
        LOG_P(TAG, "-- Adding msg %s to encounter msgs", m.c_str());
        bytes = env->NewByteArray(m.length());
        env->SetByteArrayRegion(bytes, 0, m.length(), (jbyte*)m.c_str());
        env->CallBooleanMethod(arraylistObjMsgs, jni_cache.addArray, bytes);
    }
    for (std::string m : msgs_addrs.second) {
        LOG_P(TAG, "-- Adding addr %s to dev addrs", m.c_str());
        bytes = env->NewByteArray(m.length());
        env->SetByteArrayRegion(bytes, 0, m.length(), (jbyte*)m.c_str());
        env->CallBooleanMethod(arraylistObjAddrs, jni_cache.addArray, bytes);
    }
}

JNIEXPORT void JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1updateLinkability
        (JNIEnv *env, jobject obj, jbyteArray arr)
{
    jsize len = env->GetArrayLength(arr);
    jbyte* msg = env->GetByteArrayElements(arr,0);
    
    SDDR::Event event;
    event.ParseFromArray(reinterpret_cast<uint8_t*>(msg), len);
    if(event.has_linkabilityevent())
    {
      LOG_P(TAG, "Updating advertised and listen sets");

      LinkValueList advertisedSet;
      LinkValueList listenSet;

      const SDDR::Event::LinkabilityEvent &subEvent = event.linkabilityevent();
      for(size_t e = 0; e < subEvent.entries_size(); e++)
      {
        const SDDR::Event_LinkabilityEvent_Entry &entry = subEvent.entries(e);

        const std::string &linkValueStr = entry.linkvalue();
        LinkValue linkValue(new uint8_t[linkValueStr.length()], linkValueStr.length());
        memcpy(linkValue.get(), linkValueStr.c_str(), linkValueStr.length());

        const SDDR::Event_LinkabilityEvent_Entry_ModeType &mode = entry.mode();
        switch(mode)
        {
        case SDDR::Event_LinkabilityEvent_Entry_ModeType_AdvertAndListen:
          advertisedSet.push_back(linkValue);
          LOG_P(TAG, "Adding %s to the advertised set", linkValue.toString().c_str()); 
        case SDDR::Event_LinkabilityEvent_Entry_ModeType_Listen:
          listenSet.push_back(linkValue);
          LOG_P(TAG, "Adding %s to the listen set", linkValue.toString().c_str()); 
          break; 
        default:
          LOG_E(TAG, "Received invalid mode (%d) in a linkability event entry", mode);
          break;
        }
      }

      SDDRRadio* radioPtr = getRadioPtr(env, obj);
      radioPtr->setAdvertisedSet(advertisedSet);
      radioPtr->setListenSet(listenSet);
    } else {
      LOG_P(TAG, "Not a linkable event");
    }
}

JNIEXPORT jbyteArray JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1getRetroactiveMatches
        (JNIEnv *env, jobject obj, jbyteArray arr)
{
    jsize len = env->GetArrayLength(arr);
    jbyte* msg = env->GetByteArrayElements(arr,0);
    
    // deserialize bloom filter information
    SDDR::Event event;
    event.ParseFromArray(reinterpret_cast<uint8_t*>(msg), len);
    assert(event.has_retroactiveinfo());
    std::list<BloomInfo> bis;
    const SDDR::Event::RetroactiveInfo &subEvent = event.retroactiveinfo();
    for(size_t b = 0; b < subEvent.blooms_size(); b++)
    {
        const SDDR::Event_RetroactiveInfo_BloomInfo &pbbloom = subEvent.blooms(b);

        /* Set up new bloom filter */
        assert(pbbloom.bloom().m_() == pbbloom.bloom().bits_().size());
        const size_t bloomsize = pbbloom.bloom().bits_().size(); 
        const uint8_t* bloombuffer = (const uint8_t*) pbbloom.bloom().bits_().c_str();
        BloomFilter bloom(pbbloom.bloom().n_(), pbbloom.bloom().k_(), BitMap(pbbloom.bloom().m_(), bloombuffer));

        /* Add new bloominfo to process */
        assert(pbbloom.prefix_size() == pbbloom.prefix_bytes().size());
        const uint8_t* prefixbuffer = (const uint8_t*) pbbloom.prefix_bytes().c_str();
        bis.push_back(BloomInfo(bloom, BitMap(pbbloom.prefix_size()*8, prefixbuffer), pbbloom.pfalse()));
    }

    // perform queries on bloom filters
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    LinkValueList matching;
    bool matches = radioPtr->getRetroactiveMatches(matching, bis);
    if (!matches) {
        return NULL;
    }
    LOG_P(TAG, "Retroactive: Found %d matches", matching.size());

    // serialize matching back to java array
    SDDR::Event_RetroactiveInfo *retroactiveinfo = new SDDR::Event_RetroactiveInfo();

    for(auto it = matching.begin(); it != matching.end(); it++)
    {
        retroactiveinfo->add_matchingset(it->get(), it->size());
    }
    std::string str;
    SDDR::Event fullEvent;
    fullEvent.set_allocated_retroactiveinfo(retroactiveinfo);
    fullEvent.SerializeToString(&str);

    uint8_t* bytes = (uint8_t*)str.c_str();
    const jbyte* nativemsg = reinterpret_cast<const jbyte*>(bytes);
    jbyteArray jbytes = env->NewByteArray(str.length());
    env->SetByteArrayRegion(jbytes, 0, str.length(), nativemsg);
    return jbytes;
}

JNIEXPORT jbyteArray JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1getMyDHKey(JNIEnv *env, jobject obj, jbyteArray arr) {
    return null;
}
