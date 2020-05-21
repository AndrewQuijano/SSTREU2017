package Localization;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayList;

import Localization.structs.SendLocalizationData;
import Localization.structs.LocalizationResult;
import Localization.structs.SendTrainingArray;
import security.elgamal.ElGamalCipher;
import security.elgamal.ElGamal_Ciphertext;
import ui.MainActivity;
import ui.TrainActivity;

import static ui.MainActivity.SQLDatabase;
import static ui.MainActivity.e_pk;
import static ui.MainActivity.e_sk;
import static ui.MainActivity.portNumber;

import security.DGK.DGKOperations;
import security.DGK.DGKPrivateKey;
import security.DGK.DGKPublicKey;
import security.paillier.PaillierCipher;
import security.paillier.PaillierPrivateKey;
import security.paillier.PaillierPublicKey;
import security.socialistmillionaire.bob;

public class ClientThread implements Runnable
{
    private final static String TAG = "CLIENT_THREAD";

    private ObjectOutputStream toServer = null;
    private ObjectInputStream fromServer = null;

    //Pass Data back to class by reference...
    private background findMe;
    private TrainActivity trainMe;
    private background getColumns;

    //Data Objects
    private SendTrainingArray sendTraining;     //Training Data
    private SendLocalizationData transmission;  //For Encrypted Paillier/DGK Transmission

    // Have all Keys in case comparison is needed!!
    private DGKPublicKey pubKey = MainActivity.DGKpk;
    private DGKPrivateKey privKey = MainActivity.DGKsk;
    private PaillierPublicKey pk = MainActivity.pk;
    private PaillierPrivateKey sk = MainActivity.sk;

    private LOCALIZATION_SCHEME LOCALIZATIONSCHEME;
    private Socket clientSocket;

    // Get all currently trained points
    public ClientThread(TrainActivity trainActivity)
    {
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(-3);
        this.trainMe = trainActivity;
    }

    /*
    Called from MainActivity
    Purpose: Force mySQL Database to process data from
    training data.
     */
    public ClientThread()
    {
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(-2);
    }

    ClientThread(background needMACs)
    {
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(-1);
        this.getColumns = needMACs;
    }

    // Send Training Data
    public ClientThread (SendTrainingArray in)
    {
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(0);
        this.sendTraining = in;
    }

    // For Localization
    ClientThread (SendLocalizationData input, background search, int local)
    {
        this.findMe = search;
        this.transmission = input;
        this.LOCALIZATIONSCHEME = LOCALIZATION_SCHEME.from_int(local);
    }

    //===============================SOCKET METHODS/RUN THREAD============================================

    public void run ()
    {
        Object in;
        try
        {
            clientSocket = new Socket(SQLDatabase, portNumber);
            // Prepare I/O Stream
            this.toServer = new ObjectOutputStream(clientSocket.getOutputStream());
            this.fromServer = new ObjectInputStream(clientSocket.getInputStream());
            Log.d(TAG, "I/O Streams set!");

            switch(LOCALIZATIONSCHEME)
            {
                case GETXY:
                    toServer.writeObject("Acquire all current training points");
                    toServer.flush();

                    // Following this patch the server needs to know the Phone as well
                    if(MainActivity.multi_phone)
                    {
                        String [] phone_data = MainActivity.getPhoneData();
                        for (String s: phone_data)
                        {
                            Log.d(TAG, s);
                        }
                        toServer.writeObject(phone_data);
                        toServer.flush();
                    }

                    in = fromServer.readObject();
                    if(in instanceof Double [])
                    {
                        trainMe.existingX = (Double []) in;
                    }
                    else
                    {
                        Log.d(TAG, "Data Type: -3, DIDN'T GET DOUBLE X []");
                    }

                    in = fromServer.readObject();
                    if(in instanceof Double[])
                    {
                        trainMe.existingY = (Double []) in;
                    }
                    else
                    {
                        Log.d(TAG, "Data Type: -3, DIDN'T GET DOUBLE Y []");
                    }
                    break;

                /*
                Input: Command to force Database to compute Lookup Table
                Return: NOTHING. Kill Thread after
                 */
                case PROCESS:
                    toServer.writeObject("Process LUT");
                    toServer.flush();
                    if (fromServer.readBoolean())
                    {
                        Log.d(TAG, "Successfully Processed Lookup Table!");
                        MainActivity.process_good.show();
                    }
                    else
                    {
                        Log.d(TAG, "Error Processing Lookup Tables!");
                        MainActivity.process_bad.show();
                    }
                    break;
                case GET_COLUMN:
                    toServer.writeObject("Get Lookup Columns");
                    toServer.flush();
                    in = fromServer.readObject();
                    if (in instanceof String [])
                    {
                        getColumns.CommonMAC = (String[]) in;
                    }
                    else
                    {
                        Log.d(TAG, "INVALID COLUMN RECEIVED! " + in.getClass());
                    }
                    break;
                case TRAIN:
                    toServer.writeObject(sendTraining);
                    toServer.flush();
                    //Wait to get confirmation that the data successfully inserted...
                    if (fromServer.readBoolean())
                    {
                        MainActivity.good_train.show();
                    }
                    else
                    {
                        MainActivity.bad_train.show();
                    }
                    break;

                case PLAIN_MIN:
                case PLAIN_DMA:
                case PLAIN_MCA:
                case PAILLIER_DMA:
                case PAILLIER_MCA:
                case PAILLIER_MIN:
                case DGK_MIN:
                case DGK_MCA:
                case DGK_DMA:
                case EL_GAMAL_DMA:
                case EL_GAMAL_MIN:
                case EL_GAMAL_MCA:
                    localize();
                    break;
                default:
                    Log.d(TAG, "Error at Thread run: No Valid Object was sent here");
                    break;
            }
        }
        catch (ClassCastException cce)
        {
            cce.printStackTrace();
        }
        catch (ClassNotFoundException cnf)
        {
            cnf.printStackTrace();
        }
        catch (IOException ioe)
        {
            Log.d(TAG,"CHECK IF YOU ARE CONNECTED TO WI-FI (Most Common Issue)");
            Log.d(TAG, "MAKE SURE YOU HAVE RIGHT IP ADDRESS!!!");
            Log.d(TAG, "IF YOU ARE STILL TIMING OUT, IT IS YOUR FIREWALL!");
            ioe.printStackTrace();
            //This can be caused if the phone is too far from 3rd floor of EC.
        }
    }

    private void localize() throws IOException, ClassNotFoundException
    {
        bob andrew;
        BigInteger [] location;
        BigInteger divisor;
        Object in;

        toServer.writeObject(transmission);
        toServer.flush();

        // Server will want to have an Alice/Bob instance ready just in case
        if(e_pk == null || e_sk == null)
        {
            andrew = new bob(clientSocket, new KeyPair(pk, sk), new KeyPair(pubKey, privKey));
        }
        else
        {
            andrew = new bob(clientSocket, new KeyPair(pk, sk), new KeyPair(pubKey, privKey),
                    new KeyPair(e_pk, e_sk));
        }
        switch(LOCALIZATIONSCHEME)
        {
            case PLAIN_MIN:
            case PLAIN_MCA:
            case PLAIN_DMA:
                in = fromServer.readObject();
                if(transmission.isREU2017)
                {
                    if (in instanceof Double[])
                    {
                        findMe.coordinates = (Double[]) in;
                    }
                    else
                    {
                        Log.d(TAG, "INVALID OBJECT: " + in.getClass());
                    }
                }
                else
                {
                    if (in instanceof ArrayList<?>)
                    {
                        for (Object o: (ArrayList<?>) in)
                        {
                            if(o instanceof LocalizationResult)
                            {
                                findMe.fromServer.add((LocalizationResult) o);
                            }
                        }
                    }
                    else
                    {
                        Log.d(TAG, "ERROR WRONG OBJECT IN PLAINTEXT 2015/2017! " + in.getClass());
                    }
                }
                break;

            case DGK_MIN:
            case DGK_MCA:
            case DGK_DMA:

                if(transmission.isREU2017)
                {
                    // bob is spawned
                    andrew.setDGKMode(true);
                    // Sort to get the Minimum value OR K-Minimum
                    andrew.run();
                    in = fromServer.readObject();
                    if (LOCALIZATIONSCHEME == LOCALIZATION_SCHEME.DGK_MIN)
                    {
                        if (in instanceof BigInteger[])
                        {
                            location = (BigInteger []) in;
                            findMe.coordinates[0] = (double) DGKOperations.decrypt(privKey, location[0]);
                            findMe.coordinates[1] = (double) DGKOperations.decrypt(privKey, location[1]);
                        }
                        else
                        {
                            Log.d(TAG, "INVALID OBJECT IN DGK_MIN: " + in.getClass());
                        }
                    }
                    else
                    {
                        // If DMA, divide all matches
                        divisor = DGKOperations.decrypt((BigInteger) in, privKey);
                        toServer.writeObject(divisor);
                        toServer.flush();

                        // Bob must stay alive to divide...
                        for (int i = 0; i < MainActivity.k; i++)
                        {
                            andrew.division(divisor.longValue() * (MainActivity.k - 1));
                        }

                        // Now you can get your location
                        in = fromServer.readObject();
                        if (in instanceof BigInteger[])
                        {
                            location = (BigInteger[]) in;
                            // Divide by the factor of both server/phone
                            findMe.coordinates[0] = (double) DGKOperations.decrypt(privKey, location[0]);
                            findMe.coordinates[1] = (double) DGKOperations.decrypt(privKey, location[1]);
                            findMe.coordinates[0] = findMe.coordinates[0]/MainActivity.FACTOR;
                            findMe.coordinates[1] = findMe.coordinates[0]/MainActivity.FACTOR;
                        }
                    }
                }
                else
                {
                    // REU 2015 DGK Code
                    in = fromServer.readObject();
                    Log.d(TAG, "DGK REU 2015");
                    if (in instanceof ArrayList<?>)
                    {
                        for (Object o: (ArrayList<?>) in)
                        {
                            Log.d(TAG, "OBJECT FOUND");
                            if(o instanceof LocalizationResult)
                            {
                                findMe.fromServer.add((LocalizationResult) o);
                            }
                        }
                    }
                    else
                    {
                        Log.d(TAG, "Output Loop: INVALID OBJECT SENT: " + in.getClass());
                    }
                }
                break;
            case PAILLIER_MIN:
            case PAILLIER_MCA:
            case PAILLIER_DMA:

                if(transmission.isREU2017)
                {
                    andrew.setDGKMode(false);
                    andrew.run();
                    in = fromServer.readObject();
                    if(LOCALIZATIONSCHEME == LOCALIZATION_SCHEME.PAILLIER_MIN)
                    {
                        if (in instanceof BigInteger [])
                        {
                            // Will always be DGK encrypted!
                            location = (BigInteger[]) in;
                            findMe.coordinates[0] = PaillierCipher.decrypt(location[0], sk).doubleValue();
                            findMe.coordinates[1] = PaillierCipher.decrypt(location[1], sk).doubleValue();
                        }
                        else
                        {
                            Log.d(TAG, "ERROR, INVALID OBJECT " + in.getClass());
                        }
                    }
                    else
                    {
                        divisor = PaillierCipher.decrypt((BigInteger) in, sk);
                        toServer.writeObject(divisor);
                        toServer.flush();

                        for (int i = 0; i < MainActivity.k; i++)
                        {
                            andrew.division(divisor.longValue() * (MainActivity.k - 1));
                        }

                        // Now you can get your location
                        in = fromServer.readObject();
                        if (in instanceof BigInteger[])
                        {
                            location = (BigInteger[]) in;
                            // Decrypt and Divide by the factor of both server/phone
                            findMe.coordinates[0] = PaillierCipher.decrypt(location[0], sk).doubleValue()/MainActivity.FACTOR;
                            findMe.coordinates[1] = PaillierCipher.decrypt(location[1], sk).doubleValue()/MainActivity.FACTOR;
                        }
                    }
                }
                else
                {
                    // REU 2015
                    in = fromServer.readObject();
                    if(in instanceof ArrayList<?>)
                    {
                        for (Object o: (ArrayList<?>) in)
                        {
                            if(o instanceof LocalizationResult)
                            {
                                findMe.fromServer .add((LocalizationResult) o);
                            }
                            else
                            {
                                throw new IllegalArgumentException("EXPECTED LOCALIZATION RESULT");
                            }
                        }
                    }
                    else
                    {
                        throw new IllegalArgumentException("INVALID OBJECT! " + in.getClass());
                    }
                }
            case EL_GAMAL_MIN:
            case EL_GAMAL_MCA:
            case EL_GAMAL_DMA:

                if(transmission.isREU2017)
                {
                    // TODO: IF NUMBER IS BIGGER THAN U?
                    andrew.repeat_ElGamal_Protocol4();

                    in = fromServer.readObject();
                    if(LOCALIZATIONSCHEME == LOCALIZATION_SCHEME.EL_GAMAL_MIN)
                    {
                        if (in instanceof ElGamal_Ciphertext[])
                        {
                            ElGamal_Ciphertext [] e_location = (ElGamal_Ciphertext[]) in;
                            findMe.coordinates[0] = ElGamalCipher.decrypt(e_sk, e_location[0]).doubleValue();
                            findMe.coordinates[1] = ElGamalCipher.decrypt(e_sk, e_location[1]).doubleValue();
                        }
                        else
                        {
                            Log.d(TAG, "ERROR, INVALID OBJECT " + in.getClass());
                        }
                    }
                    else
                    {
                        divisor = ElGamalCipher.decrypt(e_sk, (ElGamal_Ciphertext) in);
                        toServer.writeObject(divisor);
                        toServer.flush();

                        for (int i = 0; i < MainActivity.k; i++)
                        {
                            andrew.ElGamal_division(divisor.longValue() * (MainActivity.k - 1));
                        }

                        // Now you can get your location
                        in = fromServer.readObject();
                        if (in instanceof ElGamal_Ciphertext[])
                        {
                            ElGamal_Ciphertext [] e_location = (ElGamal_Ciphertext[]) in;
                            // Decrypt and divide by the factor of both server/phone
                            findMe.coordinates[0] = ElGamalCipher.decrypt(e_sk, e_location[0]).doubleValue()/MainActivity.FACTOR;
                            findMe.coordinates[1] = ElGamalCipher.decrypt(e_sk, e_location[1]).doubleValue()/MainActivity.FACTOR;
                        }
                    }
                }
                else
                {
                    // REU 2015
                    in = fromServer.readObject();
                    if(in instanceof ArrayList<?>)
                    {
                        for (Object o: (ArrayList<?>) in)
                        {
                            if(o instanceof LocalizationResult)
                            {
                                findMe.fromServer .add((LocalizationResult) o);
                            }
                            else
                            {
                                throw new IllegalArgumentException("EXPECTED LOCALIZATION RESULT");
                            }
                        }
                    }
                    else
                    {
                        throw new IllegalArgumentException("INVALID OBJECT! " + in.getClass());
                    }
                }
        }
    }
}