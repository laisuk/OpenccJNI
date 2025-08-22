#include "opencc_fmmseg_capi.h"
#include "opencc_OpenccWrapper.h"
#include <string>
#include <cstring>

JNIEXPORT jlong JNICALL Java_opencc_OpenccWrapper_opencc_1new(JNIEnv *env, jobject obj)
{
    return (jlong)opencc_new();
}

JNIEXPORT jbyteArray JNICALL Java_opencc_OpenccWrapper_opencc_1convert(JNIEnv *env, jobject obj, jlong instance, jbyteArray input, jbyteArray config, jboolean punctuation)
{
    // Convert jbyteArray to std::string
    auto getJByteArrayAsString = [env](jbyteArray array) -> std::string {
        jsize length = env->GetArrayLength(array);
        jbyte *elements = env->GetByteArrayElements(array, nullptr);
        std::string str(reinterpret_cast<const char*>(elements), length);
        env->ReleaseByteArrayElements(array, elements, JNI_ABORT); // Don't copy back to Java
        return str;
    };

    std::string inputString = getJByteArrayAsString(input);
    std::string configString = getJByteArrayAsString(config);

    // Call the native function with the std::string data
    char *output = opencc_convert(reinterpret_cast<void *>(instance), inputString.c_str(), configString.c_str(), punctuation);

    // Create a jbyteArray to return the output
    jbyteArray result = nullptr;
    if (output != nullptr) {
        jsize outputLength = strlen(output);  // Get the length of the output
        result = env->NewByteArray(outputLength);  // Create a new jbyteArray
        if (result != nullptr) {
            env->SetByteArrayRegion(result, 0, outputLength, reinterpret_cast<jbyte*>(output));  // Copy the output to the jbyteArray
        }
        opencc_string_free(output);  // Free the native string if necessary
    }

    return result;
}

JNIEXPORT void JNICALL Java_opencc_OpenccWrapper_opencc_1delete(JNIEnv *env, jobject obj, jlong instance) {
    opencc_delete((void *)instance);
}

JNIEXPORT jint JNICALL Java_opencc_OpenccWrapper_opencc_1zho_1check(JNIEnv *env, jobject obj, jlong instance, jbyteArray input) {
    // Convert jbyteArray to std::string
    jsize inputLength = env->GetArrayLength(input);
    jbyte *inputBytes = env->GetByteArrayElements(input, nullptr);

    // Create a std::string from the byte array
    std::string inputString(reinterpret_cast<const char*>(inputBytes), inputLength);

    // Call the native function with the std::string data
    int c_code = opencc_zho_check(reinterpret_cast<void *>(instance), inputString.c_str());

    // Release the input byte array
    env->ReleaseByteArrayElements(input, inputBytes, JNI_ABORT);

    return c_code;
}

JNIEXPORT jboolean JNICALL Java_opencc_OpenccWrapper_opencc_1get_1parallel(JNIEnv *env, jobject obj, jlong instance) {
    return opencc_get_parallel((void *)instance) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_opencc_OpenccWrapper_opencc_1set_1parallel(JNIEnv *env, jobject obj, jlong instance, jboolean is_parallel) {
    opencc_set_parallel((void *)instance, (bool)is_parallel);
}

JNIEXPORT jstring JNICALL Java_opencc_OpenccWrapper_opencc_1last_1error(JNIEnv *env, jobject obj) {
    char *last_error = opencc_last_error();
    jstring result = nullptr;

    if (last_error != NULL) {
        result = env->NewStringUTF(last_error);
         opencc_error_free(last_error);
    }
    return result;
}