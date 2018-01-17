#include "include/Logger.h"
#include "c_SDDRRadio.h"

static struct jThingsCache {
    jfieldID fidc_RadioPtr;
    jfieldID fidChangeEpoch;
    jfieldID fidDiscover;
    jfieldID fidc_EncounterMsgs;
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
JNIEXPORT jboolean JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1processScanResult
  (JNIEnv *env, jobject obj, jbyteArray jaddr, jint jrssi, jbyteArray jadvert){
    SDDRRadio* radioPtr = getRadioPtr(env, obj);

    int addr_len = env->GetArrayLength(jaddr);
    char* addr = new char[addr_len];
    env->GetByteArrayRegion(jaddr, 0, addr_len, reinterpret_cast<jbyte*>(addr));
    assert(addr_len / 8 == ADDR_LEN);
    Address myAddr(ADDR_LEN, (uint8_t*)addr);
    
    int advert_len = env->GetArrayLength(jadvert);
    char* advert = new char[advert_len];
    env->GetByteArrayRegion(jadvert, 0, advert_len, reinterpret_cast<jbyte*>(advert));
    return radioPtr->processScanResponse(myAddr, (int)jrssi, std::string(advert));
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
    std::vector<std::string> msgs = radioPtr->postDiscoveryGetEncounters();

    jobject arraylistObjMsgs = env->GetStaticObjectField(jni_cache.clsNative, 
            jni_cache.fidc_EncounterMsgs); 
    jbyteArray bytes;
    for (std::string m : msgs) {
        LOG_P(TAG, "-- Adding msg %s to encounter msgs", m.c_str());
        bytes = env->NewByteArray(m.length());
        env->SetByteArrayRegion(bytes, 0, m.length(), (jbyte*)m.c_str());
        env->CallBooleanMethod(arraylistObjMsgs, jni_cache.addArray, bytes);
    }
}

JNIEXPORT jbyteArray JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1getMyAdvert(JNIEnv *env, jobject obj) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    std::string advert = radioPtr->advert_;
    jbyteArray jbytes = env->NewByteArray(advert.length());
    env->SetByteArrayRegion(jbytes, 0, advert.length(), (jbyte*)advert.c_str());
    return jbytes;
}

JNIEXPORT jbyteArray JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1getMyDHKey(JNIEnv *env, jobject obj) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    std::string dhkey = radioPtr->dhkey_;
    jbyteArray jbytes = env->NewByteArray(dhkey.length());
    env->SetByteArrayRegion(jbytes, 0, dhkey.length(), (jbyte*)dhkey.c_str());
    return jbytes;
}

JNIEXPORT jbyteArray JNICALL Java_org_mpisws_sddrservice_encounters_SDDR_1Native_c_1computeSecretKey(JNIEnv *env, jobject obj, jbyteArray otherDHKey) {
    SDDRRadio* radioPtr = getRadioPtr(env, obj);
    std::string dhkey = radioPtr->dhkey_;
    jbyteArray jbytes = env->NewByteArray(dhkey.length());
    env->SetByteArrayRegion(jbytes, 0, dhkey.length(), (jbyte*)dhkey.c_str());
    return jbytes;
}
