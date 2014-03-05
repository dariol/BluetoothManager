package net.nycjava;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class BluetoothManager extends Activity {
	
	public static String TAG = "== BtMgr ==";

	BluetoothAdapter btadapter = null;
	BluetoothDevice btdevice;
	BluetoothClass btclass;

	ArrayAdapter<String> pairedAdapter, detectedAdapter;
	
	ArrayList<BluetoothDevice> pairedDevicesList;
	ArrayList<String> pairedListString;
	ListView pairedListView;

	ArrayList<BluetoothDevice> detectedDevicesList = null;
	ListView detectedListView;
	ListItemPairedListener pairedListListener;
	ListItemDetectedListener detectedListListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		pairedListString = new ArrayList<String>();
		pairedDevicesList = new ArrayList<BluetoothDevice>();
		pairedListListener = new ListItemPairedListener();
		detectedDevicesList = new ArrayList<BluetoothDevice>();
		
		setContentView(R.layout.main);
		pairedListView = (ListView) findViewById(R.id.pairedlist);
		detectedListView = (ListView) findViewById(R.id.detectedlist);
		pairedAdapter = new ArrayAdapter<String>(BluetoothManager.this, android.R.layout.simple_list_item_1, pairedListString);
		detectedAdapter = new ArrayAdapter<String>(BluetoothManager.this, android.R.layout.simple_list_item_single_choice);
		detectedListView.setAdapter(detectedAdapter);
		detectedListListener = new ListItemDetectedListener();
		detectedAdapter.notifyDataSetChanged();
		pairedListView.setAdapter(pairedAdapter);
		
		btadapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	protected void onStart() {
		super.onStart();
		if(btadapter!=null) {
			if(!btadapter.isEnabled()) { 
				enableBluetooth();
			}
			getPairedDevices();
			startSearch();
			pairedListView.setOnItemClickListener(pairedListListener);
			detectedListView.setOnItemClickListener(detectedListListener);
		} else {
			Toast.makeText(this, "bluetooth not supported", Toast.LENGTH_LONG).show();			
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if(mReceiver!=null) {
			BluetoothManager.this.unregisterReceiver(mReceiver);			
		}
	}
	private void getPairedDevices() {
		pairedDevicesList.clear();
		pairedAdapter.clear();
		Set<BluetoothDevice> pairedDevice = btadapter.getBondedDevices();
		if (pairedDevice.size() > 0) {
			for (BluetoothDevice device : pairedDevice) {
				pairedListString.add(device.getName() + " " + device.getAddress());
				pairedDevicesList.add(device);
			}
		}
		pairedAdapter.notifyDataSetChanged();
	}
	class ListItemPairedListener implements OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			btdevice = pairedDevicesList.get(position);
			try {
				Boolean removedBond = removeBond(btdevice);
				if (removedBond) {
					pairedListString.remove(position);
					pairedAdapter.notifyDataSetChanged();
				}
				Log.i(TAG, btdevice.getName()+": removed bond=" + removedBond);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void startSearch() {
		IntentFilter intentFilterFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		BluetoothManager.this.registerReceiver(mReceiver, intentFilterFound);
		IntentFilter intentFilterBonded = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		BluetoothManager.this.registerReceiver(mReceiver, intentFilterBonded);
		btadapter.startDiscovery();
	}
	class ListItemDetectedListener implements OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			btdevice = detectedDevicesList.get(position);
			Log.i(TAG, "device: " + btdevice.toString());
			Boolean isBonded = false;
			try {
				isBonded = createBond(btdevice);
				Log.i(TAG, btdevice.getName()+": bonded=" + isBonded);
				if(isBonded) {
					detectedDevicesList.remove(position);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			if(BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				if (detectedDevicesList.size() < 1) {
					detectedAdapter.add(device.getName() + " " + device.getAddress());
					detectedDevicesList.add(device);
					detectedAdapter.notifyDataSetChanged();
				} else {
					boolean notInList = true;
					for (int i = 0; i < detectedDevicesList.size(); i++) {
						if (device.getAddress().equals(detectedDevicesList.get(i).getAddress())) {
							notInList = false;
						}
					}
					if (notInList == true) {
						detectedAdapter.add(device.getName() + " " + device.getAddress());
						detectedDevicesList.add(device);
						detectedAdapter.notifyDataSetChanged();
					}
				}
			} else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
				if((Integer)intent.getExtras().get(BluetoothDevice.EXTRA_BOND_STATE)==BluetoothDevice.BOND_BONDED) {
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					getPairedDevices();
					pairedAdapter.notifyDataSetChanged();
					boolean connectResult = connect(device);
					Log.i(TAG,"connectResult="+connectResult );
				}
			}
		}
	};
	
	public boolean createBond(BluetoothDevice btDevice) throws Exception {
		Class<?> btdeviceclass = Class.forName("android.bluetooth.BluetoothDevice");
		Method createBondMethod = btdeviceclass.getMethod("createBond");
		Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);
		return returnValue.booleanValue();
	}
	public boolean removeBond(BluetoothDevice btDevice) throws Exception {
		Class<?> btdeviceclass = Class.forName("android.bluetooth.BluetoothDevice");
		Method removeBondMethod = btdeviceclass.getMethod("removeBond");
		Boolean returnValue = (Boolean) removeBondMethod.invoke(btDevice);
		return returnValue.booleanValue();
	}
	private Boolean connect(BluetoothDevice btDevice) {
		Boolean bool = false;
		try {
			Class<?> btdeviceclass = Class.forName("android.bluetooth.BluetoothDevice");
//			Class<?>[] parameterTypes = {};
			Method method = btdeviceclass.getMethod("connect");
			bool = (Boolean) method.invoke(btDevice);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bool.booleanValue();
    }
	private void enableBluetooth() {
		btadapter.enable();
		//definitely not the way to do this... wait for intent instead!
		for(int retries=0; !btadapter.isEnabled() && retries<10; retries++) {
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
