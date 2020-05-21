package Localization.structs;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

//import security.DGK.DGKPrivateKey;
//import security.paillier.PaillierPrivateKey;
import Localization.LOCALIZATION_SCHEME;
import security.DGK.DGKPublicKey;
import security.elgamal.ElGamalPublicKey;
import security.elgamal.ElGamal_Ciphertext;
import security.paillier.PaillierPublicKey;

public class SendLocalizationData implements Serializable
{
    //Localization Parameters
    public final LOCALIZATION_SCHEME LOCALIZATION_SCHEME;

    // PlainText Data
    public final Integer [] RSS;
    public final String  [] APs;

    // Secure Triple Data Paillier and DGK
    public final BigInteger [] S2;
    public final BigInteger [] S3_comp;
    public final BigInteger S3;

    // Secure Triple Data ElGamal
    public final List<ElGamal_Ciphertext> e_S2;
    public final List<ElGamal_Ciphertext> e_S3_comp;
    public final ElGamal_Ciphertext e_S3;

    private static final long serialVersionUID = 201194517759072124L;

    // Public Keys
    public final ElGamalPublicKey e_pk;
    public final DGKPublicKey pubKey;
    public final PaillierPublicKey pk;

    // Mode
    public final boolean isREU2017;
    
    // Phone data just in case it filtering by phone data
    public final String [] phone_data;
    
    // PlainText
    public SendLocalizationData(String [] APs, Integer [] RSS, DGKPublicKey pubKey,
                                LOCALIZATION_SCHEME local, boolean isREU2017, String [] phone_data)
    {
        this.RSS = RSS;
        this.APs = APs;
        LOCALIZATION_SCHEME = local;

        this.S2 = null;
        this.S3 = null;
        this.S3_comp = null;
        this.e_S2 = null;
        this.e_S3 = null;
        this.e_S3_comp = null;

        this.pubKey = pubKey;
        this.pk = null;
        this.e_pk = null;
        this.isREU2017 = isREU2017;
        this.phone_data = phone_data;
    }

    // Paillier, yes you need the DGK Key just in case you run comparison protocol!
    public SendLocalizationData(String [] APs, BigInteger[] S2, BigInteger S3, BigInteger [] S3_comp,
    	PaillierPublicKey pk, DGKPublicKey _pubKey, LOCALIZATION_SCHEME local, boolean isREU2017, String [] phone_data)
    {
        this.RSS = null;
        this.APs = APs;
        this.S2 = S2;
        this.S3 = S3;
        this.S3_comp = S3_comp;
        this.e_S2 = null;
        this.e_S3 = null;
        this.e_S3_comp = null;

        this.LOCALIZATION_SCHEME = local;
        this.pubKey = _pubKey;
        this.pk = pk;
        this.e_pk = null;
        this.isREU2017 = isREU2017;
        this.phone_data = phone_data;
    }

    // DGK
    public SendLocalizationData(String [] APs, BigInteger [] S2,
                   BigInteger S3, BigInteger [] S3_comp,
                          DGKPublicKey pubKey, LOCALIZATION_SCHEME local, boolean isREU2017, String [] phone_data)
    {
        this.RSS = null;
        this.APs = APs;
        this.S2 = S2;
        this.S3 = S3;
        this.S3_comp = S3_comp;
        this.e_S2 = null;
        this.e_S3 = null;
        this.e_S3_comp = null;

        this.LOCALIZATION_SCHEME = local;
        this.pubKey = pubKey;
        this.pk = null;
        this.e_pk = null;
        this.isREU2017 = isREU2017;
        this.phone_data = phone_data;
    }
    
    // ElGamal, yes you need the DGK Key just in case you run comparison protocol!
    public SendLocalizationData(String [] APs, List<ElGamal_Ciphertext> e_S2, ElGamal_Ciphertext e_S3,
                                List<ElGamal_Ciphertext> e_S3_comp,
                                ElGamalPublicKey pk, DGKPublicKey pubKey, LOCALIZATION_SCHEME local,
                                boolean isREU2017, String [] phone_data)
    {
        this.RSS = null;
        this.APs = APs;
        this.S2 = null;
        this.S3 = null;
        this.S3_comp = null;
        this.e_S2 = e_S2;
        this.e_S3 = e_S3;
        this.e_S3_comp = e_S3_comp;
        this.LOCALIZATION_SCHEME = local;
        this.pubKey = pubKey;
        this.pk = null;
        this.e_pk = pk;
        this.isREU2017 = isREU2017;
        this.phone_data = phone_data;
    }

    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException
    {
        aInputStream.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException
    {
        aOutputStream.defaultWriteObject();
    }
}