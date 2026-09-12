package com.sersoluciones.flutter_pos_printer_platform

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.annotation.NonNull
import com.sersoluciones.flutter_pos_printer_platform.usb.USBPrinterService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/**
 * FlutterPosPrinterPlatformPlugin -- variante SOLO USB.
 *
 * ⚠️ Este fork ha eliminado por completo el soporte Bluetooth/BLE (2026-09-11).
 * Motivos, por orden de importancia:
 *
 * 1. La app que lo usa imprime unicamente por USB: no habia una sola llamada a la
 *    parte Bluetooth.
 * 2. El plugin original arrastraba siete permisos que la app heredaba sin
 *    declararlos, incluidos ACCESS_FINE_LOCATION y ACCESS_COARSE_LOCATION. Una
 *    tablet de punto de venta pidiendo ubicacion precisa por un plugin de
 *    impresora es injustificable.
 * 3. El campo `bluetoothService` era `lateinit` y se asignaba en
 *    onAttachedToActivity() DESPUES de `adapter.init()`. Cuando ese init fallaba
 *    --y fallaba siempre en Android 14+, ver USBPrinterService.init()-- el campo
 *    se quedaba sin asignar y la app crasheaba al cerrarse con
 *    UninitializedPropertyAccessException. Sin ese campo, la clase entera de
 *    fallo desaparece.
 *
 * Si algun dia hace falta Bluetooth, se recupera del historial de git, no se
 * reescribe.
 */
class FlutterPosPrinterPlatformPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private final var TAG = "FlutterPosPrinterPlatformPlugin"

    private var binaryMessenger: BinaryMessenger? = null

    private var channel: MethodChannel? = null
    private var messageUSBChannel: EventChannel? = null
    private var eventUSBSink: EventChannel.EventSink? = null

    private var context: Context? = null
    private var currentActivity: Activity? = null

    lateinit var adapter: USBPrinterService

    private val usbHandler = object : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                USBPrinterService.STATE_USB_CONNECTED -> {
                    eventUSBSink?.success(2)
                }
                USBPrinterService.STATE_USB_CONNECTING -> {
                    eventUSBSink?.success(1)
                }
                USBPrinterService.STATE_USB_NONE -> {
                    eventUSBSink?.success(0)
                }
            }
        }
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onAttachedToEngine")
        binaryMessenger = flutterPluginBinding.binaryMessenger
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onDetachedFromEngine")
        channel?.setMethodCallHandler(null)
        messageUSBChannel?.setStreamHandler(null)

        messageUSBChannel = null

        // Guarda deliberada: si `adapter.init()` lanzo durante el attach, este
        // campo puede no estar asignado. Era exactamente el fallo que crasheaba
        // la app al cerrarla.
        if (this::adapter.isInitialized) {
            adapter.setHandler(null)
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.d(TAG, "onAttachedToActivity")

        context = binding.activity.applicationContext
        currentActivity = binding.activity

        channel = MethodChannel(binaryMessenger!!, methodChannel)
        channel!!.setMethodCallHandler(this)

        messageUSBChannel = EventChannel(binaryMessenger!!, eventChannelUSB)
        messageUSBChannel?.setStreamHandler(object : EventChannel.StreamHandler {

            override fun onListen(p0: Any?, sink: EventChannel.EventSink) {
                eventUSBSink = sink
            }

            override fun onCancel(p0: Any?) {
                eventUSBSink = null
            }
        })

        adapter = USBPrinterService.getInstance(usbHandler)
        adapter.init(context)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        Log.d(TAG, "onDetachedFromActivityForConfigChanges")
        currentActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        Log.d(TAG, "onReattachedToActivityForConfigChanges")
        currentActivity = binding.activity
    }

    override fun onDetachedFromActivity() {
        Log.d(TAG, "onDetachedFromActivity")
        currentActivity = null
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        Log.d(TAG, "method call " + call.method.toString())
        when {
            call.method.equals("getList") -> {
                getUSBDeviceList(result)
            }
            call.method.equals("connectPrinter") -> {
                val vendor: Int? = call.argument("vendor")
                val product: Int? = call.argument("product")
                connectPrinter(vendor, product, result)
            }
            call.method.equals("close") -> {
                closeConn(result)
            }
            call.method.equals("printText") -> {
                val text: String? = call.argument("text")
                printText(text, result)
            }
            call.method.equals("printRawData") -> {
                val raw: String? = call.argument("raw")
                printRawData(raw, result)
            }
            call.method.equals("readPrinterStatus") -> {
                result.success(adapter.readStatus())
            }
            call.method.equals("printBytes") -> {
                val bytes: ArrayList<Int>? = call.argument("bytes")
                printBytes(bytes, result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun getUSBDeviceList(result: Result) {
        val list = ArrayList<HashMap<*, *>>()
        val usbDevices: List<UsbDevice> = adapter.deviceList
        for (usbDevice in usbDevices) {
            val deviceMap: HashMap<String?, String?> = HashMap()
            deviceMap["name"] = usbDevice.deviceName
            deviceMap["manufacturer"] = usbDevice.manufacturerName
            deviceMap["product"] = usbDevice.productName
            deviceMap["deviceId"] = usbDevice.deviceId.toString()
            deviceMap["vendorId"] = usbDevice.vendorId.toString()
            deviceMap["productId"] = usbDevice.productId.toString()
            list.add(deviceMap)
        }
        result.success(list)
    }

    private fun connectPrinter(vendorId: Int?, productId: Int?, result: Result) {
        if (vendorId == null || productId == null) return
        adapter.setHandler(usbHandler)
        if (!adapter.selectDevice(vendorId, productId)) {
            result.success(false)
        } else {
            result.success(true)
        }
    }

    private fun closeConn(result: Result) {
        adapter.setHandler(usbHandler)
        adapter.closeConnectionIfExists()
        result.success(true)
    }

    private fun printText(text: String?, result: Result) {
        if (text.isNullOrEmpty()) return
        adapter.setHandler(usbHandler)
        adapter.printText(text)
        result.success(true)
    }

    private fun printRawData(base64Data: String?, result: Result) {
        if (base64Data.isNullOrEmpty()) return
        adapter.setHandler(usbHandler)
        adapter.printRawData(base64Data)
        result.success(true)
    }

    private fun printBytes(bytes: ArrayList<Int>?, result: Result) {
        if (bytes == null) {
            result.success(false)
            return
        }
        adapter.setHandler(usbHandler)
        adapter.printBytes(bytes)
        result.success(true)
    }

    companion object {
        const val methodChannel = "com.sersoluciones.flutter_pos_printer_platform"
        const val eventChannelUSB = "com.sersoluciones.flutter_pos_printer_platform/usb_state"
    }
}
