package com.sinyal.app.net

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.math.min

class LanBenchTest {

    private var server: LanBenchServer? = null

    @After fun tearDown() {
        server?.stop()
        server = null
    }

    private fun startServer(): Int {
        val s = LanBenchServer()
        s.start()
        thread { s.acceptLoop() }
        server = s
        return s.port
    }

    private fun connect(port: Int): Triple<Socket, DataInputStream, DataOutputStream> {
        val socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", port), 4_000)
        socket.soTimeout = 10_000
        return Triple(socket, DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream()))
    }

    @Test fun pingGetsPongBack() = runBlocking {
        val port = startServer()
        val (socket, input, output) = connect(port)
        socket.use {
            val rtt = LanBenchClient().pingOnce(input, output)
            assertNotNull(rtt)
            assertTrue(rtt!! >= 0.0)
        }
        Unit
    }

    @Test fun pingLoopCollectsSamples() = runBlocking {
        val port = startServer()
        val rtts = LanBenchClient().ping("127.0.0.1", port, samples = 5)
        assertEquals(5, rtts.size)
    }

    @Test fun downStreamsExactlyTheRequestedBytes() {
        val port = startServer()
        val (socket, input, output) = connect(port)
        socket.use {
            output.writeBytes("DOWN 1000000\n")
            output.flush()
            val buffer = ByteArray(16_384)
            var total = 0L
            while (total < 1_000_000L) {
                val n = input.read(buffer, 0, min(buffer.size, (1_000_000L - total).toInt()))
                if (n < 0) break
                total += n
            }
            assertEquals(1_000_000L, total)
        }
    }

    @Test fun upDrainRepliesDone() {
        val port = startServer()
        val (socket, input, output) = connect(port)
        socket.use {
            output.writeBytes("UP 65536\n")
            output.flush()
            output.write(ByteArray(65_536))
            output.flush()
            assertEquals("DONE", readCommand(input))
        }
    }

    @Test fun sizedBytesFollowsTheLinkRate() {
        val client = LanBenchClient()
        assertEquals(LanBenchClient.MIN_BYTES, client.sizedBytes(1.0))
        assertEquals(LanBenchClient.MAX_BYTES, client.sizedBytes(10_000.0))
        // ~4 s at 100 Mbps ≈ 50 MB.
        assertEquals(50_000_000L, client.sizedBytes(100.0))
    }
}
