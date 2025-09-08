/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.handlandmarker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import java.nio.ByteBuffer

/**
 * Helper class used to convert a camera frame in YUV format to a bitmap object.
 */
@Suppress("DEPRECATION")
class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val script = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    // Cache allocations so that they aren't created on every frame conversion.
    private var yuvBits: ByteBuffer? = null
    private var yuvType: Type? = null
    private var yuvAllocation: Allocation? = null
    private var rgbType: Type? = null
    private var rgbAllocation: Allocation? = null

    @Synchronized
    fun yuvToRgb(image: Image, output: Bitmap) {
        val yuvBuffer = image.planes[0].buffer
        val yuvLen = yuvBuffer.remaining()

        // This is a temporary workaround because of a bug in RenderScript that causes it not to
        // handle non-big-endian-aligned data correctly. See b/120621052.
        if (yuvBits == null || yuvBits!!.capacity() < yuvLen) {
            yuvBits = ByteBuffer.allocateDirect(yuvLen)
        }
        yuvBuffer.get(yuvBits!!.array(), 0, yuvLen)

        if (yuvAllocation == null) {
            yuvType = Type.Builder(rs, Element.U8(rs)).setX(yuvLen).create()
            yuvAllocation = Allocation.createTyped(rs, yuvType)
        }
        yuvAllocation!!.copyFrom(yuvBits!!.array())

        if (rgbAllocation == null) {
            rgbType = Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(image.width).setY(image.height).create()
            rgbAllocation = Allocation.createTyped(rs, rgbType)
        }

        script.setInput(yuvAllocation)
        script.forEach(rgbAllocation)
        rgbAllocation!!.copyTo(output)
    }
}
