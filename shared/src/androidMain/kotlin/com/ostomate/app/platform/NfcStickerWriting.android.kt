package com.ostomate.app.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.TagTechnology
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.ostomate.app.domain.StickerIoException
import com.ostomate.app.domain.StickerTag
import com.ostomate.app.domain.StickerTagFormat
import com.ostomate.app.domain.StickerWriteOutcome
import com.ostomate.app.domain.StickerWriter
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Writes stickers with NFC reader mode on the foreground Activity. While reader mode is on,
 * Android hands every tag to [NfcAdapter.ReaderCallback] and does not dispatch
 * NDEF_DISCOVERED, so holding a sticker to the phone while writing never opens the
 * `ostomate://log` link and never logs an event.
 */
class AndroidNfcStickerWriting(private val context: Context) : NfcStickerWriting {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readerActivity: WeakReference<Activity>? = null

    /** Bumped by every start/stop, so an outcome from a session that has ended is dropped. */
    private val session = AtomicInteger()

    private fun adapter(): NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    override fun status(): NfcStatus {
        val adapter = adapter() ?: return NfcStatus.NO_HARDWARE
        return if (adapter.isEnabled) NfcStatus.ON else NfcStatus.OFF
    }

    override fun start(
        link: String,
        onOutcome: (StickerWriteOutcome) -> Unit,
    ): Boolean {
        stop()
        val adapter = adapter() ?: return false
        val activity = CurrentActivityHolder.activity ?: return false
        if (!adapter.isEnabled) return false
        val mySession = session.incrementAndGet()
        adapter.enableReaderMode(
            activity,
            { tag ->
                // Binder thread: the tag I/O blocks, so it runs here and only the result is posted.
                val outcome = StickerWriter.write(AndroidStickerTag(tag), link)
                mainHandler.post { if (session.get() == mySession) onOutcome(outcome) }
            },
            READER_FLAGS,
            null,
        )
        readerActivity = WeakReference(activity)
        return true
    }

    override fun stop() {
        session.incrementAndGet()
        val activity = readerActivity?.get()
        readerActivity = null
        if (activity != null) adapter()?.disableReaderMode(activity)
    }

    override fun openNfcSettings() {
        val starter = CurrentActivityHolder.activity ?: context
        val newTask = if (starter is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            starter.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(newTask))
        } catch (_: ActivityNotFoundException) {
            starter.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(newTask))
        }
    }

    private companion object {
        const val READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
    }
}

/** Android's NFC tag behind [StickerTag]; platform exceptions become [StickerIoException]. */
internal class AndroidStickerTag(tag: Tag) : StickerTag {
    private val ndef: Ndef? = Ndef.get(tag)
    private val formatable: NdefFormatable? = if (ndef == null) NdefFormatable.get(tag) else null

    override val format: StickerTagFormat =
        when {
            ndef != null -> StickerTagFormat.NDEF
            formatable != null -> StickerTagFormat.FORMATABLE
            else -> StickerTagFormat.UNSUPPORTED
        }

    override val isWritable: Boolean get() = ndef?.isWritable == true

    override val capacityBytes: Int get() = ndef?.maxSize ?: 0

    override fun messageSize(link: String): Int = message(link).byteArrayLength

    override fun write(link: String) {
        io {
            val message = message(link)
            when {
                ndef != null -> {
                    ndef.connectIfNeeded()
                    ndef.writeNdefMessage(message)
                }
                formatable != null -> {
                    formatable.connectIfNeeded()
                    formatable.format(message)
                }
                else -> throw StickerIoException("Not an NDEF sticker")
            }
        }
    }

    /**
     * Null after formatting a blank sticker: its NDEF view only exists once Android discovers
     * it again. [StickerWriter] doesn't read back after a format for that reason.
     */
    override fun readLink(): String? =
        io {
            ndef?.let {
                it.connectIfNeeded()
                it.ndefMessage?.records?.firstOrNull()?.toUri()?.toString()
            }
        }

    override fun close() {
        io {
            ndef?.close()
            formatable?.close()
        }
    }

    private fun TagTechnology.connectIfNeeded() {
        if (!isConnected) connect()
    }

    private fun message(link: String) = NdefMessage(NdefRecord.createUri(link))

    private inline fun <T> io(block: () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            // Includes TagLostException: the sticker moved away.
            throw StickerIoException(e.message, e)
        } catch (e: FormatException) {
            throw StickerIoException(e.message, e)
        } catch (e: SecurityException) {
            // "Tag is out of date": a stale handle to a sticker that has gone.
            throw StickerIoException(e.message, e)
        } catch (e: IllegalStateException) {
            throw StickerIoException(e.message, e)
        }
}
