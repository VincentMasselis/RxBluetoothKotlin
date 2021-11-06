#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define pin_button1 25
#define pin_button2 26
#define pin_LED 33

//store some IO states into variables
int ledState = LOW;

/***********BLE********/
BLEServer *pServer = NULL;
BLECharacteristic * pButton1Characteristic;
BLECharacteristic * pButton2Characteristic;
BLECharacteristic * pLEDCharacteristic;
BLECharacteristic * pLONGCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;

#define SERVICE_UUID            "6E400001-B5A3-F393-E0A9-E50E24DCCA9E" // UART service UUID
#define CHARACTERISTIC_BUTTON_1 "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_BUTTON_2 "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_LED      "6E400004-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_LONG_DATA      "6E400005-B5A3-F393-E0A9-E50E24DCCA9E"

std::string longdata = "This is a long piece of text because I want to test sending around 500 bytes of data over BLE. This should be possible even though the standard is only 20 bytes. However, it seems that sending more than 20 bytes is still supposed to be possible with an ESP32 and within the BLE protocol. This won't be that close to 512 bytes but that's not the point. The point is to send a long string of data and this should work maybe I think I hope.";
//std::string longdata = "this is a test";

//server callbacks, used for managing connections and such
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
    }
};

//callback for a writable BLE characteristic, this is where we receive our data from 1 characteristic.
class LEDcallback: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string value = pCharacteristic->getValue();

      if (value.length() > 0) {
        Serial.println("*********");
        Serial.println("Received Value: ");
        for (int i = 0; i < value.length(); i++)
          Serial.println(value[i], BIN);


        Serial.println("*********");
        if (value[0] == 1) {
          ledState = HIGH;
        } else {
          ledState = LOW;
        }
        digitalWrite(pin_LED, ledState);
      }
    }
};

class LongCallback: public BLECharacteristicCallbacks {
    void onRead(BLECharacteristic *pCharacteristic) {
      Serial.println("received read request");
    }
};
/*********** ENDBLE********/

void IRAM_ATTR button1_ISR()
{
  int buttonvalue1 = digitalRead(pin_button1);
  //Send button 1 data
  pButton1Characteristic->setValue((uint8_t*)&buttonvalue1, 2);
  pButton1Characteristic->notify();
}

void IRAM_ATTR button2_ISR()
{
  int buttonvalue2 = digitalRead(pin_button2);
  //send button 2 data
  pButton2Characteristic->setValue((uint8_t*)&buttonvalue2, 2);
  pButton2Characteristic->notify();
}


void setup() {
  Serial.begin(115200);

  pinMode(pin_LED, OUTPUT);
  pinMode(pin_button1, INPUT_PULLUP);
  pinMode(pin_button2, INPUT_PULLUP);

  attachInterrupt(pin_button1, button1_ISR, CHANGE);
  attachInterrupt(pin_button2, button2_ISR, CHANGE);


  // Create the BLE Device
  BLEDevice::init("IO Service");

  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *p_ioService = pServer->createService(SERVICE_UUID);

  // Create a BLE Characteristic for button 1
  pButton1Characteristic = p_ioService->createCharacteristic(
                             CHARACTERISTIC_BUTTON_1,
                             BLECharacteristic::PROPERTY_NOTIFY
                           );

  pButton1Characteristic->addDescriptor(new BLE2902());

  // Create a BLE Characteristic for button 2
  pButton2Characteristic = p_ioService->createCharacteristic(
                             CHARACTERISTIC_BUTTON_2,
                             BLECharacteristic::PROPERTY_NOTIFY
                           );

  pButton2Characteristic->addDescriptor(new BLE2902());

  //Create a BLE characteristic for the LED
  pLEDCharacteristic = p_ioService->createCharacteristic(
                         CHARACTERISTIC_LED,
                         BLECharacteristic::PROPERTY_WRITE |
                         BLECharacteristic::PROPERTY_READ |
                         BLECharacteristic::PROPERTY_NOTIFY
                       );

  pLEDCharacteristic->addDescriptor(new BLE2902());


  pLEDCharacteristic->setCallbacks(new LEDcallback());

  //Create a BLE characteristic for the LED
  pLONGCharacteristic = p_ioService->createCharacteristic(
                          CHARACTERISTIC_LONG_DATA,
                          BLECharacteristic::PROPERTY_READ
                        );

  pLONGCharacteristic->setCallbacks(new LongCallback());

  // Start the service
  p_ioService->start();

  // Start advertising
  pServer->getAdvertising()->start();
  Serial.println("Waiting a client connection to notify...");

}

void loop() {

  if (deviceConnected) {

    //send LED data
    pLEDCharacteristic->setValue((uint8_t*)&ledState, 2);
    pLEDCharacteristic->notify();

    pLONGCharacteristic->setValue(longdata);
    delay(10); // bluetooth stack will go into congestion, if too many packets are sent
  }

  // disconnecting
  if (!deviceConnected && oldDeviceConnected) {
    delay(500); // give the bluetooth stack the chance to get things ready
    pServer->startAdvertising(); // restart advertising
    Serial.println("start advertising");
    oldDeviceConnected = deviceConnected;
  }
  // connecting
  if (deviceConnected && !oldDeviceConnected) {
    // do stuff here on connecting
    oldDeviceConnected = deviceConnected;
  }
}
