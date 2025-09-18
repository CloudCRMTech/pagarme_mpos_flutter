package me.williampereira.pagarme_mpos_flutter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import me.pagar.mposandroid.EmvApplication;
import me.pagar.mposandroid.Mpos;
import me.pagar.mposandroid.MposListener;
import me.pagar.mposandroid.MposPaymentResult;

public class PagarmeMposFlutterPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

  private Mpos mpos = null;
  private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
  private Context context;
  private EventChannel.EventSink eventSink;
  private Handler uiThreadHandler = new Handler(Looper.getMainLooper());

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    context = flutterPluginBinding.getApplicationContext();

    final MethodChannel channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "pagarme_mpos_flutter");
    channel.setMethodCallHandler(this);

    final EventChannel eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "mpos_stream");
    eventChannel.setStreamHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {
      case "getPlatformVersion":
        result.success("Android " + android.os.Build.VERSION.RELEASE);
        break;

      case "createMpos":
        try {
          this.CreateMpos((String) call.argument("deviceName"), (String) call.argument("encryptionKey"));
          result.success(true);
        } catch (IOException e) {
          result.error("CreateMposError", e.getMessage(), null);
        }
        break;

      case "initialize":
        this.Initialize();
        result.success(true);
        break;

      case "downloadEmvTablesToDevice":
        try {
          this.DownloadEmvTablesToDevice((boolean) call.argument("forceUpdate"));
          result.success(true);
        } catch (Exception e) {
          result.error("EMVDownloadError", e.getMessage(), null);
        }
        break;

      case "payAmount":
        try {
          this.PayAmount((Integer) call.argument("amount"), (ArrayList<String>) call.argument("cardBrandList"), (Integer) call.argument("paymentMethod"));
          result.success(true);
        } catch (Exception e) {
          result.error("PayAmountError", e.getMessage(), null);
        }
        break;

      case "close":
        this.Close((String) call.argument("message"));
        result.success(true);
        break;

      case "closeConnection":
        this.CloseConnection();
        result.success(true);
        break;

      case "finishTransaction":
        try {
          this.FinishTransaction((Boolean) call.argument("connected"), (Integer) call.argument("responseCode"), (String) call.argument("emvData"));
          result.success(true);
        } catch (Exception e) {
          result.error("FinishTransactionError", e.getMessage(), null);
        }
        break;

      case "openConnection":
        this.OpenConnection((Boolean) call.argument("secure"));
        result.success(true);
        break;

      case "displayText":
        this.DisplayText((String) call.argument("message"));
        result.success(true);
        break;

      default:
        result.notImplemented();
        break;
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    this.eventSink = null;
    this.context = null;
  }

  private void CreateMpos(String deviceName, String encryptionKey) throws IOException {
    List<BluetoothDevice> pairedDevices = new ArrayList<>(bluetoothAdapter.getBondedDevices());
    BluetoothDevice bluetoothDevice = findByAddress(deviceName, pairedDevices);
    mpos = new Mpos(bluetoothDevice, encryptionKey, context);
    this.setUpListeners();
  }

  private void Initialize() {
    mpos.initialize();
  }

  private void DownloadEmvTablesToDevice(Boolean forceUpdate) throws Exception {
    mpos.downloadEMVTablesToDevice(forceUpdate);
  }

  private void Close(String message) {
    mpos.close(message);
  }

  private void CloseConnection() {
    mpos.closeConnection();
  }

  private void FinishTransaction(Boolean connected, Integer responseCode, String emvData) {
    mpos.finishTransaction(connected, responseCode, emvData);
  }

  private void OpenConnection(Boolean secure) {
    mpos.openConnection(secure);
  }

  private void DisplayText(String message) {
    mpos.displayText(message);
  }

  private void PayAmount(Integer amount, ArrayList<String> cardBrandList, Integer paymentMethod) throws Exception {
    mpos.payAmount(amount, toEmvApplicationsList(cardBrandList, paymentMethod), paymentMethod);
  }

  static private HashMap<String, String> toResultMap(String cardHash, MposPaymentResult result) {
    HashMap<String, String> resultDart = new HashMap<>();
    resultDart.put("cardFirstDigits", result.cardFirstDigits);
    resultDart.put("cardLastDigits", result.cardLastDigits);
    resultDart.put("cardBrand", result.cardBrand);
    resultDart.put("localTransactionId", result.localTransactionId);
    resultDart.put("paymentMethod", String.valueOf(result.paymentMethod));
    resultDart.put("isOnline", String.valueOf(result.isOnline));
    resultDart.put("shouldFinishTransaction", String.valueOf(result.shouldFinishTransaction));
    resultDart.put("cardHash", cardHash);
    return resultDart;
  }

  static private List<EmvApplication> toEmvApplicationsList(ArrayList<String> cardBrandList, int paymentMethod) throws Exception {
    List<EmvApplication> emvApplicationsList = new ArrayList<>();
    if (cardBrandList != null && !cardBrandList.isEmpty()) {
      for (int i = cardBrandList.size() - 1; i >= 0; i--) {
        String cardBrand = cardBrandList.get(i);
        emvApplicationsList.add(new EmvApplication(paymentMethod, cardBrand));
      }
    }
    return emvApplicationsList;
  }

  static private BluetoothDevice findByAddress(String deviceName, List<BluetoothDevice> deviceList) {
    for (BluetoothDevice device : deviceList) {
      if (device.getName().equals(deviceName)) return device;
    }
    return null;
  }

  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    this.eventSink = events;
  }

  @Override
  public void onCancel(Object arguments) {
    this.eventSink = null;
  }

  private HashMap<String, String> getEvent(String methodName, String value) {
    HashMap<String, String> event = new HashMap<>();
    event.put("method", methodName);
    event.put("value", value);
    return event;
  }

  private void setUpListeners() {
    mpos.addListener(new MposListener() {
      public void bluetoothConnected() {
        sendEvent("onBluetoothConnected", null);
      }

      public void bluetoothDisconnected() {
        sendEvent("onBluetoothDisconnected", null);
      }

      public void bluetoothErrored(final int error) {
        sendEvent("onBluetoothErrored", String.valueOf(error));
      }

      public void receiveInitialization() {
        sendEvent("onReceiveInitialization", null);
      }

      public void receiveNotification(final String notification) {
        sendEvent("onReceiveNotification", notification);
      }

      public void receiveTableUpdated(final boolean loaded) {
        sendEvent("onReceiveTableUpdated", String.valueOf(loaded));
      }

      public void receiveFinishTransaction() {
        sendEvent("onReceiveFinishTransaction", null);
      }

      public void receiveClose() {
        sendEvent("onReceiveClose", null);
      }

      public void receiveCardHash(String cardHash, MposPaymentResult result) {
        Gson gson = new Gson();
        String resultJson = gson.toJson(toResultMap(cardHash, result));

        final HashMap<String, String> event = new HashMap<>();
        event.put("method", "onReceiveCardHash");
        event.put("value", resultJson);

        uiThreadHandler.post(() -> eventSink.success(event));
      }

      public void receiveError(final int error) {
        sendEvent("onReceiveError", String.valueOf(error));
      }

      public void receiveOperationCancelled() {
        sendEvent("onReceiveOperationCancelled", null);
      }

      public void receiveOperationCompleted() {
        sendEvent("onReceiveOperationCompleted", null);
      }
    });
  }

  private void sendEvent(String method, String value) {
    if (eventSink != null) {
      uiThreadHandler.post(() -> eventSink.success(getEvent(method, value)));
    }
  }
}
