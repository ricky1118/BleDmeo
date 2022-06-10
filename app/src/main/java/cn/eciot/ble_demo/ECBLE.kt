package cn.eciot.ble_demo

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.util.Log
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.experimental.and

//创建全局Context
class MyApplication:Application(){
    companion object{
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }
}
/********************/

object ECBLE {

//    var bleContext: Context? = null
 //*********************************************************************************************
// ***********************蓝牙扫描回调        BluetoothAdapter.leScanCallback********************
// *********************************************************************************************
var scanCallback: (name: String, rssi: Int) -> Unit = { _, _ -> }//lambda表达式中不需要处理的参数用下划线，函数类型
    var scanFlag: Boolean = false

    class bleDevice(var name: String, var rssi: Int, var bluetoothDevice: BluetoothDevice)

    var deviceList: MutableList<bleDevice> = ArrayList()
    var bluetoothAdapter: BluetoothAdapter? = null
    var leScanCallback =
        BluetoothAdapter.LeScanCallback { bluetoothDevice: BluetoothDevice, rssi: Int, bytes: ByteArray ->
            //BluetoothAdapter.LeScanCallback(BluetoothDevice device,   int rssi, byte[] scanRecord)
            // byte[] scanRecord: 远程端蓝牙的广播数据， device  远程l蓝牙设备，rssi信号强度
            Log.e("bleDiscovery", bluetoothDevice.name + "|" + rssi)
            Log.e("bleDiscovery-bytes-len", "" + bytes.size)
            Log.e("bleDiscovery-bytes", "" + bytesToHexString(bytes))
            if (bluetoothDevice.name == null) return@LeScanCallback
            var isExist: Boolean = false
            for (item in deviceList) {
                if (item.name == bluetoothDevice.name) {
                    item.rssi = rssi
                    item.bluetoothDevice = bluetoothDevice
                    isExist = true
                    break;
                }
            }
            if (!isExist) {
                deviceList.add(bleDevice(bluetoothDevice.name, rssi, bluetoothDevice))
            }
            scanCallback(bluetoothDevice.name, rssi)
        }

    //**********************************************
    // ********蓝牙连接回调 BluetoothGattCallback
    // ***********************************************

    var bluetoothGatt: BluetoothGatt? = null
   //******蓝牙连接回调开始*****
    var bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
//            Log.e("onConnectionStateChange", "status=" + status + "|" + "newState=" + newState);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connectCallback(false, status)
                connectCallback = { _, _ -> }
                connectionStateChangeCallback(false)
                connectionStateChangeCallback = { _ -> }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                stopBluetoothDevicesDiscovery()
                connectCallback(true, 0)
                connectCallback = { _, _ -> }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGatt?.close()
                connectCallback(false, 0)
                connectCallback = { _, _ -> }
                connectionStateChangeCallback(false)
                connectionStateChangeCallback = { _ -> }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            bluetoothGatt = gatt
            val bluetoothGattServices = gatt?.services
            val servicesList: MutableList<String> = ArrayList()
            if (bluetoothGattServices == null) getServicesCallback(servicesList)
            else {
                for (item in bluetoothGattServices) {
//                    Log.e("ble-service", "UUID=:" + item.uuid.toString())
                    servicesList.add(item.uuid.toString())
                }
                getServicesCallback(servicesList)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val bytes = characteristic?.value
            if (bytes != null) {
//                Log.e("ble-receive", "读取成功[hex]:" + bytesToHexString(bytes));
//                Log.e("ble-receive", "读取成功[string]:" + String(bytes));
                characteristicChangedCallback(bytesToHexString(bytes), String(bytes))
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
//            if (BluetoothGatt.GATT_SUCCESS == status) {
//                Log.e("BleService", "onMtuChanged success MTU = " + mtu)
//            } else {
//                Log.e("BleService", "onMtuChanged fail ");
//            }
        }



    }//

    var connectCallback: (ok: Boolean, errCode: Int)-> Unit =  { _, _ -> }
    var reconnectTime = 0
    var connectionStateChangeCallback: (ok: Boolean)-> Unit  = { _ -> }
    var getServicesCallback: (servicesList: List<String>) -> Unit = { _ -> }
    var characteristicChangedCallback: (hex: String, string: String) -> Unit = { _, _ -> }
    /*******************以上是连接回调部分******************************************************************************************/
    //**********************************************//
    val ecServerId = "0000FFF0-0000-1000-8000-00805F9B34FB"
    val ecWriteCharacteristicId = "0000FFF2-0000-1000-8000-00805F9B34FB"
    val ecReadCharacteristicId = "0000FFF1-0000-1000-8000-00805F9B34FB"

    private fun isLocServiceEnable(context: Context): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps: Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network: Boolean =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return gps || network
    }

    fun bluetoothAdapterInit(context: Context): Int {

        //bleContext = context
        if (bluetoothAdapter == null) bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothGatt?.close()
        if (bluetoothAdapter == null) {
            //设备不支持蓝牙
            return 1
        }
        if (!isLocServiceEnable(context)) {
            //定位开关没有开
            return 2
        }
        if (!getBluetoothAdapterState()) {
            openBluetoothAdapter()
            return 3
        }
        return 0
    }

    private fun openBluetoothAdapter() {
        bluetoothAdapter?.enable()
    }

    fun closeBluetoothAdapter() {
        bluetoothAdapter?.disable()
    }

    private fun getBluetoothAdapterState(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    fun startBluetoothDevicesDiscovery(callback: (name: String, rssi: Int) -> Unit) {
        scanCallback = callback
        if (!scanFlag) {
        //     BluetoothLeScanner.startScan(leScanCallback)
            bluetoothAdapter?.startLeScan(leScanCallback)
            scanFlag = true
        }
    }

    fun stopBluetoothDevicesDiscovery() {
        if (scanFlag) {
       //     BluetoothLeScanner.stop
            bluetoothAdapter?.stopLeScan(leScanCallback)
            scanFlag = false
        }
    }
//创建连接
    private fun createBLEConnection(name: String, callback: (ok: Boolean, errCode: Int) -> Unit) {
        connectCallback = callback
        connectionStateChangeCallback = { _ -> }
        var isExist: Boolean = false
        for (item in deviceList) {
            if (item.name == name) {
                bluetoothGatt =
                    item.bluetoothDevice.connectGatt(/**bleContext**/MyApplication.context, false, bluetoothGattCallback);
                isExist = true
                break;
            }
        }
        if (!isExist) {
            connectCallback(false, -1)
        }
    }
//断开连接
    fun closeBLEConnection() {
        bluetoothGatt?.disconnect()
    }
/*******************************************************
 *服务查找
 *参数为找到的所有服务列表的结果回调,本例中在调用时为使用参数
 * ****************************************************/
    private fun getBLEDeviceServices(callback: (servicesList: List<String>) -> Unit) {
        getServicesCallback = callback
        bluetoothGatt?.discoverServices()
    }
/***********************************************
 * 获得服务的特征列表
 * 返回基于指定服务的所有特征列表
 * ********************************************/
    //    ECBLE.getBLEDeviceCharacteristics("0000fff0-0000-1000-8000-00805f9b34fb")
    private fun getBLEDeviceCharacteristics(serviceId: String): MutableList<String> {
        val service = bluetoothGatt?.getService(UUID.fromString(serviceId))
        val listGattCharacteristic = service?.characteristics
        val characteristicsList: MutableList<String> = ArrayList()
        if (listGattCharacteristic == null) return characteristicsList
        for (item in listGattCharacteristic) {
//            Log.e("ble-characteristic", "UUID=:" + item.uuid.toString())
            characteristicsList.add(item.uuid.toString())
        }
        return characteristicsList
    }
/**************************************************************************
 *
 *
 * ***********************************************************************/
    private fun notifyBLECharacteristicValueChange(
        serviceId: String,
        characteristicId: String//调用的时候赋值成ecReadCharacteristicId
    ): Boolean {
        val service = bluetoothGatt?.getService(UUID.fromString(serviceId)) ?: return false//获得相应服务的service,如果服务是空则返回false
        val characteristicRead = service.getCharacteristic(UUID.fromString(characteristicId))//获得服务下特定的特征
        val res =
            bluetoothGatt?.setCharacteristicNotification(characteristicRead, true) ?: return false//设定读特征
        if (!res) return false//如果上一条的特征值是空则返回false,否者继续往下
        //遍历特征的所有描述，并将其描述相应使能值写入特征描述
        for (dp in characteristicRead.descriptors) {
            dp.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(dp)
        }
        return true
    }
/***************************************************
 * 获得蓝牙发过来数据，参数为回调onCharacteristicChanged获得的
 * *************************************************/
    fun onBLECharacteristicValueChange(callback2: (hex: String, string: String) -> Unit) {
        characteristicChangedCallback = callback2
    }
/**************************************************************
 * 和蓝牙建立连接次外层函数
 * 调用：createBLEConnect    getBLEDeviceServices
 * getBLEDeviceCharacteristics
 * notifyBLECharacteristicValueChange
 * 并赋值状态callback值，建立一个延时线程并设定MTU数值长度
 * ************************************************************/
    fun easyOneConnect(name: String, callback: (ok: Boolean) -> Unit)  {
        createBLEConnection(name) { ok: Boolean, errCode: Int ->
//            Log.e("Connection", "res:" + ok + "|" + errCode)
            if (ok) {
//                onBLECharacteristicValueChange { hex: String, string: String ->
//                    Log.e("hex", hex)callback: (ok: Boolean) -> Unit)
//                    Log.e("string", string)
//                }
                getBLEDeviceServices() {
//                    for (item in it) {
//                        Log.e("ble-service", "UUID=" + item)
//                    }
                    getBLEDeviceCharacteristics(ecServerId)
                    notifyBLECharacteristicValueChange(ecServerId, ecReadCharacteristicId)
                    callback(true)
                    Thread() {
                        Thread.sleep(300);
                        setMtu(500)
                    }.start()
                }
            } else {
                callback(false)
            }
        }
    }
    /**************************************************************
     * 和蓝牙建立连接 最外层函数
     * 调用：次外层函数 easyOneConnect
     * ************************************************************/
    fun easyConnect(name: String, callback: (ok: Boolean) -> Unit) {
        easyOneConnect(name) {
            if (it) {
                reconnectTime = 0
                callback(true)
            } else {
                //记录重新连接次数
                reconnectTime = reconnectTime + 1
                //如果连接大于4次，清零并标定状态callback为false
                if(reconnectTime>4){
                    reconnectTime = 0
                    callback(false)
                }
                else{//否者继续创建连接线程再调用连接
                    thread(start = true) {
                        easyConnect(name,callback)
                    }
                }
            }
        }
    }
   //连接状态回调，
    fun onBLEConnectionStateChange(callback: (ok: Boolean) -> Unit) {
        connectionStateChangeCallback = callback
    }
  //连接状态回调,
    fun offBLEConnectionStateChange() {
        connectionStateChangeCallback = { _ -> }
    }

/********************************************************************************
 * 向蓝牙发送数据
 *
 * *****************************************************************************/
    private fun writeBLECharacteristicValue(
        serviceId: String,
        characteristicId: String,
        data: String,
        isHex: Boolean
    ) {
        val byteArray: ByteArray? = if (isHex) toByteArray(data)
        else data.toByteArray()

        val service = bluetoothGatt?.getService(UUID.fromString(serviceId))
        val characteristicWrite = service?.getCharacteristic(UUID.fromString(characteristicId));

        characteristicWrite?.value = byteArray
        //设置回复形式
        characteristicWrite?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        //开始写数据
        bluetoothGatt?.writeCharacteristic(characteristicWrite)
    }

    fun easySendData(data: String, isHex: Boolean) {
        writeBLECharacteristicValue(ecServerId, ecWriteCharacteristicId, data, isHex)
    }
/*****************************************************************************************************************************/

/**********************************************************
 * 设顶MTU数据宽度
 *
 * *******************************************************************/
    fun setMtu(v: Int) {
        //安卓5.0以上版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothGatt?.requestMtu(v)
        }
    }
/***************************************************************
 * byteToHexString
 * to byteArray
 * *************************************************************/
    fun bytesToHexString(bytes: ByteArray?): String {
        if (bytes == null) return ""
        var str = ""
        for (b in bytes) {
            str += String.format("%02X", b)
        }
        return str
    }

    private fun toByteArray(hexString: String): ByteArray? {
        val byteArray = ByteArray(hexString.length / 2)
        var k = 0
        for (i in byteArray.indices) {
            val high =
                (Character.digit(hexString[k], 16) and 0xf).toByte()
            val low =
                (Character.digit(hexString[k + 1], 16) and 0xf).toByte()
            byteArray[i] = (high * 16 + low).toByte()
            k += 2
        }
        return byteArray
    }
}
