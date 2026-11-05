package com.andrerinas.openheadunit.connection

interface AccessoryConnection {
    val isSingleMessage: Boolean
    // MobileCarConnect (open-headunit 754e257e): the bulk OUT endpoint's packet size; 0 = unknown or not USB.
    val outMaxPacketSize: Int get() = 0
    fun sendBlocking(buf: ByteArray, length: Int, timeout: Int): Int
    fun recvBlocking(buf: ByteArray, length: Int, timeout: Int, readFully: Boolean): Int
    val isConnected: Boolean
    suspend fun connect(): Boolean
    fun disconnect()
}
