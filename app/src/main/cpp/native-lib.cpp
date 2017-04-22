/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <string.h>
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

typedef struct
{
    uint8_t alpha;
    uint8_t red;
    uint8_t green;
    uint8_t blue;
} argb;



/* This is a trivial JNI example where we use a native method
 * to return a new VM String. See the corresponding Java source
 * file located at:
 *
 *   hello-jni/app/src/main/java/com/example/hellojni/HelloJni.java
 */

extern "C" {
JNIEXPORT jstring JNICALL
Java_com_kru13_httpserver_HttpServerActivity_stringFromJNI(JNIEnv *env,
                                                           jobject thiz) {

#if defined(__arm__)
#if defined(__ARM_ARCH_7A__)
#if defined(__ARM_NEON__)
#if defined(__ARM_PCS_VFP)
#define ABI "armeabi-v7a/NEON (hard-float)"
#else
#define ABI "armeabi-v7a/NEON"
#endif
#else
#if defined(__ARM_PCS_VFP)
#define ABI "armeabi-v7a (hard-float)"
#else
#define ABI "armeabi-v7a"
#endif
#endif
#else
#define ABI "armeabi"
#endif
#elif defined(__i386__)
#define ABI "x86"
#elif defined(__x86_64__)
#define ABI "x86_64"
#elif defined(__mips64)  /* mips64el-* toolchain defines __mips__ too */
#define ABI "mips64"
#elif defined(__mips__)
#define ABI "mips"
#elif defined(__aarch64__)
#define ABI "arm64-v8a"
#else
#define ABI "unknown"
#endif


    return env->NewStringUTF("Hello from JNI !  Compiled with ABI " ABI ".");
}


JNIEXPORT void JNICALL
Java_com_kru13_httpserver_SocketServer_changeToFractalPicture(JNIEnv *env,
                                                              jobject thiz, jobject bitmapcolor,
                                                              unsigned MaxIterations) {


    //Mat image(600, 800, CV_8UC3, Scalar(0, 0, 0));
    AndroidBitmapInfo  infocolor;
    void*              pixelscolor;
    int                ret;
    int             y;
    int             x;


    if ((ret = AndroidBitmap_getInfo(env, bitmapcolor, &infocolor)) < 0) {
        return;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmapcolor, &pixelscolor)) < 0) {
        return;
    }

    argb color;
    color.red = 0;
    color.green = 0;
    color.blue = 0;

    double ImageHeight = infocolor.height;
    double ImageWidth = infocolor.width;

    double MinRe = -2.0;
    double MaxRe = 1.0;
    double MinIm = -1.2;
    double MaxIm = MinIm + (MaxRe - MinRe) * ImageHeight / ImageWidth;
    double Re_factor = (MaxRe - MinRe) / (ImageWidth - 1);
    double Im_factor = (MaxIm - MinIm) / (ImageHeight - 1);




    for (unsigned y = 0; y < ImageHeight; ++y) {
        argb * line = (argb *) pixelscolor;
        double c_im = MaxIm - y * Im_factor;
        unsigned n;
        for (unsigned x = 0; x < ImageWidth; ++x) {
            double c_re = MinRe + x * Re_factor;
            double Z_re = c_re, Z_im = c_im;

            for (n = 0; n < MaxIterations; ++n) {
                double Z_re2 = Z_re * Z_re, Z_im2 = Z_im * Z_im;

                if (Z_re2 + Z_im2 > 4) {
                    color.green = 255 - n;    //color[1] = 255 - log(n)*100.0;
                    color.red = n;          //color[2] = log(n)*100.0;


                    line[x].green = color.green;
                    line[x].red = color.red;
                    //image.at<Vec3b>(Point(x, y)) = color;
                    break;
                }

                Z_im = 2 * Z_re * Z_im + c_im;
                Z_re = Z_re2 - Z_im2 + c_re;
            }
        }

        pixelscolor = (char *)pixelscolor + infocolor.stride;
    }

    AndroidBitmap_unlockPixels(env, bitmapcolor);

}
}






/*

JNIEXPORT void JNICALL Java_com_msi_ibm_ndk_IBMPhotoPhun_convertToGray(JNIEnv
                                                                       * env, jobject  obj, jobject bitmapcolor,jobject bitmapgray)
{
    AndroidBitmapInfo  infocolor;
    void*              pixelscolor;
    AndroidBitmapInfo  infogray;
    void*              pixelsgray;
    int                ret;
    int             y;
    int             x;



    if ((ret = AndroidBitmap_getInfo(env, bitmapcolor, &infocolor)) < 0) {
        return;
    }


    if ((ret = AndroidBitmap_getInfo(env, bitmapgray, &infogray)) < 0) {

        return;
    }



    if (infocolor.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }



    if (infogray.format != ANDROID_BITMAP_FORMAT_A_8) {
        return;
    }


    if ((ret = AndroidBitmap_lockPixels(env, bitmapcolor, &pixelscolor)) < 0) {

    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmapgray, &pixelsgray)) < 0) {
    }

    // modify pixels with image processing algorithm

    for (y=0;y<infocolor.height;y++) {
        argb * line = (argb *) pixelscolor;
        uint8_t * grayline = (uint8_t *) pixelsgray;
        for (x=0;x<infocolor.width;x++) {
            grayline[x] = 0.3 * line[x].red + 0.59 * line[x].green + 0.11*line[x].blue;
        }

        pixelscolor = (char *)pixelscolor + infocolor.stride;
        pixelsgray = (char *) pixelsgray + infogray.stride;
    }


    AndroidBitmap_unlockPixels(env, bitmapcolor);
    AndroidBitmap_unlockPixels(env, bitmapgray);


}
 */
