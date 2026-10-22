package com.yapnix.game.recording

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Draws software-composited game frames to a hardware video encoder's input surface. */
internal class EncoderSurface(private val input: Surface, private val width: Int, private val height: Int) {
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var window = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private var position = -1
    private var textureCoordinate = -1
    private var sampler = -1
    private var textureReady = false
    private val vertices = floatArrayOf(
        -1f, -1f, 0f, 1f,
        1f, -1f, 1f, 1f,
        -1f, 1f, 0f, 0f,
        1f, 1f, 1f, 0f,
    ).let { values ->
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(values); position(0) }
    }

    fun prepare() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Could not initialize EGL" }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "No recordable EGL configuration"
        }
        val config = checkNotNull(configs[0])
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Could not create recording GL context" }
        window = EGL14.eglCreateWindowSurface(display, config, input, intArrayOf(EGL14.EGL_NONE), 0)
        check(window != EGL14.EGL_NO_SURFACE) { "Could not create recording EGL surface" }
        check(EGL14.eglMakeCurrent(display, window, window, context)) { "Could not bind recording surface" }

        val vertex = compile(GLES20.GL_VERTEX_SHADER, """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoordinate;
            varying vec2 vTextureCoordinate;
            void main() {
                gl_Position = aPosition;
                vTextureCoordinate = aTextureCoordinate;
            }
        """.trimIndent())
        val fragment = try {
            compile(GLES20.GL_FRAGMENT_SHADER, """
                precision mediump float;
                uniform sampler2D uTexture;
                varying vec2 vTextureCoordinate;
                void main() { gl_FragColor = texture2D(uTexture, vTextureCoordinate); }
            """.trimIndent())
        } catch (error: Exception) {
            GLES20.glDeleteShader(vertex)
            throw error
        }
        try {
            program = GLES20.glCreateProgram()
            check(program != 0) { "Could not create recording GL program" }
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            check(linked[0] == GLES20.GL_TRUE) { "Recording shader link: ${GLES20.glGetProgramInfoLog(program)}" }
        } finally {
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
        }
        position = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordinate = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
        sampler = GLES20.glGetUniformLocation(program, "uTexture")
        check(position >= 0 && textureCoordinate >= 0 && sampler >= 0) { "Missing recording shader attributes" }
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        texture = textures[0]
        check(texture != 0) { "Could not create recording texture" }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGl()
    }

    /** Must be called on the same worker thread as [prepare] and [close]. */
    fun render(bitmap: Bitmap, timestampNanos: Long) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        if (textureReady) GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            textureReady = true
        }
        GLES20.glUniform1i(sampler, 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(2)
        GLES20.glVertexAttribPointer(textureCoordinate, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(textureCoordinate)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGl()
        check(EGLExt.eglPresentationTimeANDROID(display, window, timestampNanos)) { "Could not timestamp video frame" }
        check(EGL14.eglSwapBuffers(display, window)) { "Could not submit video frame" }
    }

    fun close() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT && window != EGL14.EGL_NO_SURFACE) {
                if (EGL14.eglMakeCurrent(display, window, window, context)) {
                    if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                    if (program != 0) GLES20.glDeleteProgram(program)
                }
            }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        window = EGL14.EGL_NO_SURFACE
        input.release()
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Could not create recording shader" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] != GLES20.GL_TRUE) {
            val detail = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Recording shader compile: $detail")
        }
        return shader
    }

    private fun checkGl() {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { "Recording GL error: 0x${error.toString(16)}" }
    }

    private companion object { const val EGL_RECORDABLE_ANDROID = 0x3142 }
}
