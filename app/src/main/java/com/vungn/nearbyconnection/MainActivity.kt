package com.vungn.nearbyconnection

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.material.textfield.TextInputEditText
import java.util.Random


class MainActivity : AppCompatActivity() {
    private val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
    private lateinit var endpointId: String
    private lateinit var discoveryButton: Button
    private lateinit var friendMessage: TextView
    private lateinit var myMessage: TextView
    private lateinit var input: TextInputEditText
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkAndRequestLocationPermissions(permission)
        startAdvertising()
        discoveryButton = findViewById(R.id.discovery)
        friendMessage = findViewById(R.id.left)
        myMessage = findViewById(R.id.right)
        input = findViewById(R.id.textInput)
        sendButton = findViewById(R.id.send)
        discoveryButton.setOnClickListener {
            startDiscovery()
        }
        friendMessage.visibility = View.GONE
        myMessage.visibility = View.GONE
        input.visibility = View.GONE
        sendButton.visibility = View.GONE
        sendButton.setOnClickListener {
            val string = input.text.toString()
            val bytesPayload = Payload.fromBytes(string.toByteArray())
            Nearby.getConnectionsClient(this@MainActivity).sendPayload(endpointId, bytesPayload)
            myMessage.text = string
            input.setText("")
        }
    }

    private fun startAdvertising() {
        val advertisingOptions: AdvertisingOptions =
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        Nearby.getConnectionsClient(this).startAdvertising(
            generateRandomName(), SERVICE_ID, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener { Log.d(TAG, "startAdvertising: Success") }
            .addOnFailureListener { e: Exception? -> e?.printStackTrace() }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        Nearby.getConnectionsClient(this)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { Log.d(TAG, "startDiscovery") }
            .addOnFailureListener { e: java.lang.Exception? ->
                Log.w(
                    TAG,
                    "startDiscovery ${e?.message}"
                )
            }
    }

    private fun generateRandomName(): String {
        var name = ""
        val random = Random()
        for (i in 0..4) {
            name += random.nextInt(10)
        }
        Log.d(TAG, "Random name: $name")
        return name
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(p0: String, p1: ConnectionInfo) {
            // Automatically accept the connection on both sides.

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Accept connection to " + p1.endpointName)
                .setMessage("Confirm the code matches on both devices: " + p1.authenticationDigits)
                .setPositiveButton(
                    "Accept"
                ) { dialog: DialogInterface?, which: Int ->  // The user confirmed, so we can accept the connection.
                    Nearby.getConnectionsClient(this@MainActivity)
                        .acceptConnection(p0, payloadCallback)
                }.setNegativeButton(
                    android.R.string.cancel
                ) { dialog: DialogInterface?, which: Int ->  // The user canceled, so we should reject the connection.
                    Nearby.getConnectionsClient(this@MainActivity).rejectConnection(p0)
                }.setIcon(android.R.drawable.ic_dialog_alert).show()
        }

        override fun onConnectionResult(p0: String, p1: ConnectionResolution) {
            when (p1.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "onConnectionResult: OK")
                    Log.d(
                        TAG,
                        "EndpointId: $p0,\n ${p1.status.statusCode}\n${p1.status.status}\n${p1.status.statusMessage}"
                    )
                    endpointId = p0
                    discoveryButton.visibility = View.GONE
                    friendMessage.visibility = View.VISIBLE
                    myMessage.visibility = View.VISIBLE
                    input.visibility = View.VISIBLE
                    sendButton.visibility = View.VISIBLE
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "onConnectionResult: rejected")
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.w(TAG, "onConnectionResult: error")
                }

                else -> {}
            }
        }

        override fun onDisconnected(p0: String) {
            Log.d(TAG, "onDisconnected")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(p0: String, p1: DiscoveredEndpointInfo) {
            Log.d(TAG, "onEndpointFound")
            Log.d(
                TAG,
                "Discovered endpoint Info\n serverid: ${p1.serviceId},\n endpointName: ${p1.endpointName},\n endpointInfo: ${
                    String(p1.endpointInfo)
                }\n EndpointID: $p0"
            )
            Nearby.getConnectionsClient(this@MainActivity)
                .requestConnection(generateRandomName(), p0, connectionLifecycleCallback)
                .addOnSuccessListener { Log.d(TAG, "onEndpointFound: Success") }
                .addOnFailureListener { e: java.lang.Exception? ->
                    Log.w(TAG, "onEndpointFound error: $e")
                }
        }

        override fun onEndpointLost(p0: String) {
            Log.d(TAG, "onEndpointLost")
        }

    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(p0: String, p1: Payload) {
            Log.d(TAG, "onPayloadReceived")
            if (p1.type == Payload.Type.BYTES) {
                val receivedBytes: ByteArray = p1.asBytes()!!
                Log.d(TAG, "Byte: ${String(receivedBytes)}")
                friendMessage.text = String(receivedBytes)
            }
        }

        override fun onPayloadTransferUpdate(p0: String, p1: PayloadTransferUpdate) {
            Log.d(TAG, "onPayloadTransferUpdate")
        }
    }

    private fun checkAndRequestLocationPermissions(
        permissions: Array<String>,
    ) {
        if (permissions.all {
                ContextCompat.checkSelfPermission(
                    this, it
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            // Use location because permissions are already granted
            Log.d(TAG, "Permissions are granted")
        } else {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CODE)
        }
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
        private const val SERVICE_ID = "com.vungn.nearbyconnection.SERVICE_ID"
        private const val REQUEST_PERMISSION_CODE = 1
    }

}

