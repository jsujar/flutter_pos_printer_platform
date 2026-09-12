package com.sersoluciones.flutter_pos_printer_platform.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Handler
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.sersoluciones.flutter_pos_printer_platform.R
import java.nio.charset.Charset
import java.util.*

class USBPrinterService private constructor(private var mHandler: Handler?) {
    private var mContext: Context? = null
    private var mUSBManager: UsbManager? = null
    private var mPermissionIndent: PendingIntent? = null
    private var mUsbDevice: UsbDevice? = null
    private var mUsbDeviceConnection: UsbDeviceConnection? = null
    private var mUsbInterface: UsbInterface? = null
    private var mEndPoint: UsbEndpoint? = null

    /// Endpoint de ENTRADA (bulk IN). El plugin original lo ignoraba: solo
    /// cogia el de salida, asi que la impresora no podia contarnos nada.
    /// Necesario para preguntarle su estado (papel, tapa, errores).
    private var mEndPointIn: UsbEndpoint? = null
    var state: Int = STATE_USB_NONE

    fun setHandler(handler: Handler?) {
        mHandler = handler
    }

    private val mUsbDeviceReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if ((ACTION_USB_PERMISSION == action)) {
                synchronized(this) {
                    val usbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(
                            LOG_TAG,
                            "Success get permission for device ${usbDevice?.deviceId}, vendor_id: ${usbDevice?.vendorId} product_id: ${usbDevice?.productId}"
                        )
                        mUsbDevice = usbDevice
                        state = STATE_USB_CONNECTED
                        mHandler?.obtainMessage(STATE_USB_CONNECTED)?.sendToTarget()
                    } else {
                        Toast.makeText(context, mContext?.getString(R.string.user_refuse_perm) + ": ${usbDevice!!.deviceName}", Toast.LENGTH_LONG).show()
                        state = STATE_USB_NONE
                        mHandler?.obtainMessage(STATE_USB_NONE)?.sendToTarget()
                    }
                }
            } else if ((UsbManager.ACTION_USB_DEVICE_DETACHED == action)) {

                if (mUsbDevice != null) {
                    Toast.makeText(context, mContext?.getString(R.string.device_off), Toast.LENGTH_LONG).show()
                    closeConnectionIfExists()
                    state = STATE_USB_NONE
                    mHandler?.obtainMessage(STATE_USB_NONE)?.sendToTarget()
                }

            } else if ((UsbManager.ACTION_USB_DEVICE_ATTACHED == action)) {
//                if (mUsbDevice != null) {
//                    Toast.makeText(context, "USB device has been turned off", Toast.LENGTH_LONG).show()
//                    closeConnectionIfExists()
//                }
            }
        }
    }

    fun init(reactContext: Context?) {
        mContext = reactContext
        mUSBManager = mContext!!.getSystemService(Context.USB_SERVICE) as UsbManager
        // Android 14+ (API 34) prohibe crear un PendingIntent con FLAG_MUTABLE y un
        // Intent implicito. La solucion NO es FLAG_IMMUTABLE: el permiso USB necesita
        // que el sistema rellene EXTRA_DEVICE y EXTRA_PERMISSION_GRANTED en el intent
        // de respuesta, y con FLAG_IMMUTABLE esos extras no llegan -- el receptor de
        // abajo leeria getBooleanExtra(EXTRA_PERMISSION_GRANTED, false) y el permiso
        // pareceria denegado siempre. Lo correcto es mantener FLAG_MUTABLE y hacer el
        // Intent explicito con setPackage().
        val permissionIntent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(mContext!!.packageName)
        }
        mPermissionIndent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(mContext, 0, permissionIntent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getBroadcast(mContext, 0, permissionIntent, 0)
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        // Desde API 33 hay que declarar si el receptor esta exportado cuando el filtro
        // incluye una accion propia (ACTION_USB_PERMISSION lo es). NOT_EXPORTED es lo
        // correcto: nadie mas debe poder disparar esto, y los broadcasts del sistema
        // (ACTION_USB_DEVICE_DETACHED) siguen llegando igual.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            mContext!!.registerReceiver(mUsbDeviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            mContext!!.registerReceiver(mUsbDeviceReceiver, filter)
        }
        Log.v(LOG_TAG, "ESC/POS Printer initialized")
    }

    fun closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection!!.releaseInterface(mUsbInterface)
            mUsbDeviceConnection!!.close()
            mUsbInterface = null
            mEndPoint = null
            mUsbDevice = null
            mUsbDeviceConnection = null
        }
    }

    val deviceList: List<UsbDevice>
        get() {
            if (mUSBManager == null) {
                Toast.makeText(mContext, mContext?.getString(R.string.not_usb_manager), Toast.LENGTH_LONG).show()
                return emptyList()
            }
            return ArrayList(mUSBManager!!.deviceList.values)
        }

    fun selectDevice(vendorId: Int, productId: Int): Boolean {
//        Log.v(LOG_TAG, " status usb ______ $state")
        if ((mUsbDevice == null) || (mUsbDevice!!.vendorId != vendorId) || (mUsbDevice!!.productId != productId)) {
            synchronized(printLock) {
                closeConnectionIfExists()
                val usbDevices: List<UsbDevice> = deviceList
                for (usbDevice: UsbDevice in usbDevices) {
                    if ((usbDevice.vendorId == vendorId) && (usbDevice.productId == productId)) {
                        Log.v(LOG_TAG, "Request for device: vendor_id: " + usbDevice.vendorId + ", product_id: " + usbDevice.productId)
                        closeConnectionIfExists()
                        mUSBManager!!.requestPermission(usbDevice, mPermissionIndent)
                        state = STATE_USB_CONNECTING
                        mHandler?.obtainMessage(STATE_USB_CONNECTING)?.sendToTarget()
                        return true
                    }
                }
                return false
            }
        } else {
            mHandler?.obtainMessage(state)?.sendToTarget()
        }

        return true
    }

    private fun openConnection(): Boolean {
        if (mUsbDevice == null) {
            Log.e(LOG_TAG, "USB Device is not initialized")
            return false
        }
        if (mUSBManager == null) {
            Log.e(LOG_TAG, "USB Manager is not initialized")
            return false
        }
        if (mUsbDeviceConnection != null) {
            Log.i(LOG_TAG, "USB Connection already connected")
            return true
        }
        val usbInterface = mUsbDevice!!.getInterface(0)

        // Se recorre primero la interfaz entera para quedarse con el endpoint de
        // entrada, si lo hay. No todas las impresoras lo exponen.
        mEndPointIn = null
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_IN
            ) {
                mEndPointIn = ep
            }
        }

        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    val usbDeviceConnection = mUSBManager!!.openDevice(mUsbDevice)
                    if (usbDeviceConnection == null) {
                        Log.e(LOG_TAG, "Failed to open USB Connection")
                        return false
                    }
                    Toast.makeText(mContext, mContext?.getString(R.string.connected_device), Toast.LENGTH_SHORT).show()
                    return if (usbDeviceConnection.claimInterface(usbInterface, true)) {
                        mEndPoint = ep
                        mUsbInterface = usbInterface
                        mUsbDeviceConnection = usbDeviceConnection
                        true
                    } else {
                        usbDeviceConnection.close()
                        Log.e(LOG_TAG, "Failed to retrieve usb connection")
                        false
                    }
                }
            }
        }
        return true
    }

    /**
     * Pregunta a la impresora su estado con el comando ESC/POS `DLE EOT n`
     * (0x10 0x04 n) y devuelve el byte que responde para cada n.
     *
     * n = 1 estado general | 2 estado offline (incluye TAPA ABIERTA)
     * n = 3 estado de error | 4 sensor de papel
     *
     * Devuelve una lista de cuatro enteros; -1 en las posiciones donde la
     * impresora no contesto. Una impresora muda --las hay, y muchas-- devolvera
     * cuatro -1 aunque exponga el endpoint de entrada.
     *
     * Este metodo NO existe en el plugin original: se anadio en este fork
     * (2026-09-11) porque sin el es imposible saber si falta papel o la tapa
     * esta abierta, y una comanda que no sale es un pedido que nadie prepara.
     */
    fun readStatus(): List<Int> {
        val resultados = mutableListOf<Int>()

        if (!openConnection() || mEndPointIn == null) {
            Log.w(LOG_TAG, "Sin endpoint de entrada: la impresora no puede responder")
            return listOf(-1, -1, -1, -1)
        }

        synchronized(printLock) {
            for (n in 1..4) {
                val comando = byteArrayOf(0x10, 0x04, n.toByte())
                val escrito = mUsbDeviceConnection!!.bulkTransfer(
                    mEndPoint, comando, comando.size, TIMEOUT_ESTADO
                )

                if (escrito < 0) {
                    resultados.add(-1)
                    continue
                }

                val buffer = ByteArray(8)
                val leido = mUsbDeviceConnection!!.bulkTransfer(
                    mEndPointIn, buffer, buffer.size, TIMEOUT_ESTADO
                )

                resultados.add(if (leido > 0) buffer[0].toInt() and 0xFF else -1)
            }
        }

        Log.i(LOG_TAG, "Estado de la impresora (DLE EOT 1..4): $resultados")
        return resultados
    }

    fun printText(text: String): Boolean {
        Log.v(LOG_TAG, "Printing text")
        val isConnected = openConnection()
        return if (isConnected) {
            Log.v(LOG_TAG, "Connected to device")
            Thread {
                synchronized(printLock) {
                    val bytes: ByteArray = text.toByteArray(Charset.forName("UTF-8"))
                    val b: Int = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                    Log.i(LOG_TAG, "Return code: $b")
                }
            }.start()
            true
        } else {
            Log.v(LOG_TAG, "Failed to connect to device")
            false
        }
    }

    fun printRawData(data: String): Boolean {
        Log.v(LOG_TAG, "Printing raw data: $data")
        val isConnected = openConnection()
        return if (isConnected) {
            Log.v(LOG_TAG, "Connected to device")
            Thread {
                synchronized(printLock) {
                    val bytes: ByteArray = Base64.decode(data, Base64.DEFAULT)
                    val b: Int = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, bytes, bytes.size, 100000)
                    Log.i(LOG_TAG, "Return code: $b")
                }
            }.start()
            true
        } else {
            Log.v(LOG_TAG, "Failed to connected to device")
            false
        }
    }

    fun printBytes(bytes: ArrayList<Int>): Boolean {
        Log.v(LOG_TAG, "Printing bytes")
        val isConnected = openConnection()
        if (isConnected) {
            val chunkSize = mEndPoint!!.maxPacketSize
            Log.v(LOG_TAG, "Max Packet Size: $chunkSize")
            Log.v(LOG_TAG, "Connected to device")
            Thread {
                synchronized(printLock) {
                    val vectorData: Vector<Byte> = Vector()
                    for (i in bytes.indices) {
                        val `val`: Int = bytes[i]
                        vectorData.add(`val`.toByte())
                    }
                    val temp: Array<Any> = vectorData.toTypedArray()
                    val byteData = ByteArray(temp.size)
                    for (i in temp.indices) {
                        byteData[i] = temp[i] as Byte
                    }
                    var b = 0
                    if (mUsbDeviceConnection != null) {
                        if (byteData.size > chunkSize) {
                            var chunks: Int = byteData.size / chunkSize
                            if (byteData.size % chunkSize > 0) {
                                ++chunks
                            }
                            for (i in 0 until chunks) {
//                                val buffer: ByteArray = byteData.copyOfRange(i * chunkSize, chunkSize + i * chunkSize)
                                val buffer: ByteArray = Arrays.copyOfRange(byteData, i * chunkSize, chunkSize + i * chunkSize)
                                b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, buffer, chunkSize, 100000)
                            }
                        } else {
                            b = mUsbDeviceConnection!!.bulkTransfer(mEndPoint, byteData, byteData.size, 100000)
                        }
                        Log.i(LOG_TAG, "Return code: $b")
                    }
                }
            }.start()
            return true
        } else {
            Log.v(LOG_TAG, "Failed to connected to device")
            return false
        }
    }

    companion object {
        /// Corto: si la impresora no contesta enseguida, es que no va a
        /// contestar. No se puede bloquear el hilo esperando a una impresora
        /// muda.
        private const val TIMEOUT_ESTADO = 500

        @SuppressLint("StaticFieldLeak")
        private var mInstance: USBPrinterService? = null
        private const val LOG_TAG = "ESC POS Printer"
        private const val ACTION_USB_PERMISSION = "com.flutter_pos_printer.USB_PERMISSION"

        // Constants that indicate the current connection state
        const val STATE_USB_NONE = 0 // we're doing nothing
        const val STATE_USB_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_USB_CONNECTED = 3 // now connected to a remote device

        private val printLock = Any()

        fun getInstance(handler: Handler): USBPrinterService {
            if (mInstance == null) {
                mInstance = USBPrinterService(handler)
            }
            return mInstance!!
        }
    }
}