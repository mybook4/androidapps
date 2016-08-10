package com.example.bluetoothstereowaker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class BluetoothStereoWakerReceiver extends BroadcastReceiver implements BluetoothA2DPRequester.Callback {

    private static final String TAG = "BluetoothStereoWakerReceiver";
    private static final String FOLLOWER = "BT Audio";
    private static final String LEADER = "My Car";

    /**
     * Local reference to the device's BluetoothAdapter and teh BluetoothA2DPRequester
     */
    private BluetoothAdapter mAdapter;


    /**
     * Local data member to tell us whether we need to connect or disconnect
     */
    private enum ConnectAction {CONNECT, DISCONNECT};
    private ConnectAction connectAction;

    @Override
    public void onReceive(final Context context, Intent intent) {

        try {
            String action = intent.getAction();

            if(action == null) {
                Log.e(TAG, "Received null action");
                return;
            }

            if(action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device.getName().equalsIgnoreCase(LEADER)) {
                    String tmpMsg = new String("Detected connection to " + LEADER + ".  Attempting to also connect to " + FOLLOWER);
                    Log.d(TAG, tmpMsg);
                    Toast.makeText(context, tmpMsg, Toast.LENGTH_SHORT).show();

                    connectAction = ConnectAction.CONNECT;

                    mAdapter = BluetoothAdapter.getDefaultAdapter();
                    new BluetoothA2DPRequester(this).request(context, mAdapter);


                } else {
                    Log.d(TAG, "A bluetooth device connected (" + device.getName() + "), but it's not " + LEADER);
                }

            } else if(action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device.getName().equalsIgnoreCase(LEADER)) {
                    String tmpMsg = new String("Detected disconnection to " + LEADER + ".  Attempting to also disconnect from " + FOLLOWER);
                    Log.d(TAG, tmpMsg);
                    Toast.makeText(context, tmpMsg, Toast.LENGTH_SHORT).show();

                    mAdapter = BluetoothAdapter.getDefaultAdapter();
                    connectAction = ConnectAction.DISCONNECT;
                    new BluetoothA2DPRequester(this).request(context, mAdapter);

                } else {
                    Log.d(TAG, "A bluetooth device disconnected (" + device.getName() + "), but it's not " + LEADER);
                }



            } else {
                Log.e(TAG, "Irrelavent broadcast received");
            }

        } catch(Exception e) {
            Log.e(TAG, "Something went wrong! Check out the exception: ");
            e.printStackTrace();
        }


    }

    @Override
    public void onA2DPProxyReceived(BluetoothA2dp proxy) {

        Method theMethod;

        if(connectAction == ConnectAction.CONNECT) {
            theMethod = getConnectMethod();

        } else if(connectAction == ConnectAction.DISCONNECT) {
            theMethod = getDisconnectMethod();

        } else {
            Log.e(TAG, "Unexpected.  connectAction was neither CONNECT no DISCONNECT.");
            return;
        }

        BluetoothDevice device = findBondedDeviceByName(mAdapter, FOLLOWER);

        //If either is null, just return. The errors have already been logged
        if (theMethod == null || device == null) {
            Log.e(TAG, "Unexpected.  Either theMethod or device was null");
            return;
        }


        try {
            theMethod.setAccessible(true);
            theMethod.invoke(proxy, device);

        } catch (InvocationTargetException ex) {
            Log.e(TAG, "Unable to invoke method on proxy. " + ex.toString());

        } catch (IllegalAccessException ex) {
            Log.e(TAG, "Illegal Access! " + ex.toString());
        }

    }



    /**
     * Wrapper around some reflection code to get the hidden 'connect()' method
     * @return the connect(BluetoothDevice) method, or null if it could not be found
     */
    private Method getConnectMethod () {

        try {
            return BluetoothA2dp.class.getDeclaredMethod("connect", BluetoothDevice.class);

        } catch (NoSuchMethodException ex) {
            Log.e(TAG, "Unable to find connect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }


    /**
     * Wrapper around some reflection code to get the hidden 'disconnect()' method
     * @return the disconnect(BluetoothDevice) method, or null if it could not be found
     */
    private Method getDisconnectMethod () {

        try {
            return BluetoothA2dp.class.getDeclaredMethod("disconnect", BluetoothDevice.class);

        } catch (NoSuchMethodException ex) {
            Log.e(TAG, "Unable to find disconnect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }


    /**
     * Search the set of bonded devices in the BluetoothAdapter for one that matches
     * the given name
     * @param adapter the BluetoothAdapter whose bonded devices should be queried
     * @param name the name of the device to search for
     * @return the BluetoothDevice by the given name (if found); null if it was not found
     */
    private static BluetoothDevice findBondedDeviceByName (BluetoothAdapter adapter, String name) {

        for (BluetoothDevice device : getBondedDevices(adapter)) {
            if (name.matches(device.getName())) {
                Log.v(TAG, String.format("Found device with name %s and address %s.", device.getName(), device.getAddress()));
                return device;
            }
        }

        Log.w(TAG, String.format("Unable to find device with name %s.", name));

        return null;
    }


    /**
     * Safety wrapper around BluetoothAdapter#getBondedDevices() that is guaranteed
     * to return a non-null result
     * @param adapter the BluetoothAdapter whose bonded devices should be obtained
     * @return the set of all bonded devices to the adapter; an empty set if there was an error
     */
    private static Set<BluetoothDevice> getBondedDevices (BluetoothAdapter adapter) {

        Set<BluetoothDevice> results = adapter.getBondedDevices();

        if (results == null) {
            results = new HashSet<BluetoothDevice>();
        }

        return results;
    }

}
