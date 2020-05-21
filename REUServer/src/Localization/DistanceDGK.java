package Localization;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;

import Localization.structs.LocalizationResult;
import Localization.structs.SendLocalizationData;
import security.DGK.DGKOperations;
//import security.DGK.DGKPrivateKey;
import security.DGK.DGKPublicKey;
import security.socialistmillionaire.alice;

public class DistanceDGK extends Distance
{
	private BigInteger [] S2;
	private BigInteger S3;
	private BigInteger [] S3_comp;
	
	private DGKPublicKey pk = null;
	private boolean isREU2017;
	
	public DistanceDGK(SendLocalizationData in) 
			throws ClassNotFoundException, SQLException
	{
		scanAPs = in.APs;
		S2 = in.S2;
		S3 = in.S3;
		S3_comp = in.S3_comp;
		isREU2017 = in.isREU2017;
		pk = in.pubKey;
		if(column == null)
		{
			column = LocalizationLUT.getColumnMAC();
		}
		// Read from Database
		if(server.multi_phone)
		{
			MultiphoneLocalization.getPlainLookup(this.RSS_ij, this.coordinates, in.phone_data);
		}
		else
		{
			LocalizationLUT.getPlainLookup(this.RSS_ij, this.coordinates);
		}
		//MINIMUM_AP_MATCH = (int) (VECTOR_SIZE * FSF);
		// THIS NUMBER SHOULD ALWAYS BE >= 1 FOR THE FOLLOWING REASONS
		// 1- Inform User if they are not in floor map
		// 2- MCA/DMA will break because division by 0 becomes possible!
		MINIMUM_AP_MATCH = 1;
	}

	protected ArrayList<LocalizationResult> MinimumDistance(alice Niu) 
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		resultList = this.MissConstantAlgorithm();
		// 2015, let the phone do the work!
		if(!isREU2017)
		{
			return resultList;
		}
		// 1- Encrypt and Store coordinates
		for(int i = 0; i < resultList.size(); i++)
		{
			resultList.get(i).add_secret_coordinates(pk);
		}
		// 2- Shuffle Result List
		Collections.shuffle(resultList);
				
		// 3- Get Min and return ([[x]], [[y]])
		BigInteger min = Niu.getKMin(encryptedDistance, 1)[0];
		for(LocalizationResult l: resultList)
		{
			if(l.encryptedDistance.equals(min))
			{
				this.encryptedLocation[0] = l.encryptedCoordinates[0];
				this.encryptedLocation[1] = l.encryptedCoordinates[1];
				break;
			}
		}
		return resultList;
	}

	protected ArrayList<LocalizationResult> MissConstantAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{	
		long count = 0;
		BigInteger d = null;
		BigInteger S1_Row = null;
		BigInteger S2_Row = null;
		
		for (int i = 0; i < RSS_ij.size();i++)
		{	
			// Step 1, Compute FSF
			count = 0;
			for (int j = 0; j < VECTOR_SIZE; j++)
			{
				if(scanAPs[j].equals(column[j]))
				{
					++count;
				}
			}
			
			// Step 2, if FSF is NOT satisfied, skip this step!
			if(count < MINIMUM_AP_MATCH)
			{
				continue;
			}
			
			// Repeat MCA/DMA as shown in the paper to compute distance
			S1_Row = pk.ZERO();
			S2_Row = pk.ZERO();
			
			for (int j = 0; j < VECTOR_SIZE;j++)
			{
				if(scanAPs[j].equals(column[j]))
				{
					S1_Row = DGKOperations.add_plaintext(pk, S1_Row, RSS_ij.get(i)[j] * RSS_ij.get(i)[j]);
					S2_Row = DGKOperations.add(pk, S2_Row, DGKOperations.multiply(pk, S2[j], RSS_ij.get(i)[j].longValue()));
				}
				else
				{
					S1_Row = DGKOperations.add_plaintext(pk, S1_Row, -120 * -120);
					S2_Row = DGKOperations.add(pk, S2_Row, DGKOperations.multiply(pk, S2[j], -120));
				}
			}
			d = DGKOperations.add(pk, S1_Row, S3);
			d = DGKOperations.add(pk, d, S2_Row);
			encryptedDistance.add(d);
			resultList.add(new LocalizationResult(coordinates.get(i)[0], coordinates.get(i)[1], d, null));
		}
		return resultList;
	}

	protected ArrayList<LocalizationResult> DynamicMatchingAlgorithm()
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		long count = 0;
		BigInteger d = null;
		BigInteger S1_Row = null;
		BigInteger S2_Row = null;
		BigInteger S3_Row = null;
		
		for (int i = 0; i < RSS_ij.size();i++)
		{	
			// Step 1, Compute FSF
			count = 0;
			for (int j = 0; j < VECTOR_SIZE; j++)
			{
				if(scanAPs[j].equals(column[j]))
				{
					++count;
				}
			}
			
			// Step 2, if FSF is NOT satisfied, skip this step!
			if(count < MINIMUM_AP_MATCH)
			{
				continue;
			}
			
			// Repeat MCA/DMA as shown in the paper to compute distance
			S1_Row = pk.ZERO();
			S2_Row = pk.ZERO();
			S3_Row = pk.ZERO();
			
			for (int j = 0; j < VECTOR_SIZE;j++)
			{
				if(scanAPs[j].equals(column[j]))
				{
					S1_Row = DGKOperations.add_plaintext(pk, S1_Row, RSS_ij.get(i)[j] * RSS_ij.get(i)[j]);
					S2_Row = DGKOperations.add(pk, S2_Row, DGKOperations.multiply(pk, S2[j], RSS_ij.get(i)[j].longValue()));
					S3_Row = DGKOperations.add(pk, S3_Row, S3_comp[j]);
				}
			}
			d = DGKOperations.add(pk, S1_Row, S3_Row);
			d = DGKOperations.add(pk, d, S2_Row);
			encryptedDistance.add(d);
			resultList.add(new LocalizationResult(coordinates.get(i)[0], coordinates.get(i)[1], d, count));
		}
		return resultList;
	}

	protected BigInteger[] Phase3(alice Niu) 
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{	
		// Get the K-minimum distances!
		BigInteger [] k_min = Niu.getKMin(encryptedDistance, k);
		
		// Continue with Phase 3 of centriod finding
		Object x;
		BigInteger divisor = null;
		BigInteger [] weights = new BigInteger[Distance.k];
		
		divisor = DGKOperations.sum(pk, k_min, Distance.k);
		Niu.writeObject(divisor);
		
		// Get the plain text value from Alice
		x = Niu.readObject();
		if(x instanceof BigInteger)
		{
			divisor = (BigInteger) x;
		}
		else
		{
			throw new IllegalArgumentException("Did not recive d from the Phone!");
		}
		
		// Now I get the k distances and divide by divisor
		/*
		 * Original:
		 * (1 - d_i/sum(d_i))/(k - 1)
		 * 
		 * = 1/k-1 - d_i/sum_(d_i)(k - 1)
		 * MULTIPLY BY 100 THE WHOLE THING
		 * 100/(k - 1) - 100d_i/sum(d_i)/(k - 1)
		 */
		for (int i = 0; i < Distance.k; i++)
		{
			weights[i] = DGKOperations.multiply(pk, k_min[i], FACTOR);
			weights[i] = Niu.division(weights[i], divisor.longValue() * (k - 1));
			weights[i] = DGKOperations.subtract(pk, DGKOperations.encrypt(FACTOR/(k - 1), pk), weights[i]);
		}
		encryptedLocation[0] = pk.ZERO();
		encryptedLocation[1] = pk.ZERO();
		
		int index = -1;
		// Now I multiply it with all scalars. (x, y)
		for (int i = 0; i < Distance.k; i++)
		{
			// NOTE, IT WILL NOT GIVE ME CORRECT X_I, Y_I since it is NOT sorted.
			// So I need to get the correct index!
			index = distance_index(k_min[i]);
			encryptedLocation[0] = DGKOperations.add(pk, encryptedLocation[0], DGKOperations.multiply(pk, weights[i] , resultList.get(index).getX().longValue()));
			encryptedLocation[1] = DGKOperations.add(pk, encryptedLocation[1], DGKOperations.multiply(pk, weights[i] , resultList.get(index).getY().longValue()));
		}
		return encryptedLocation;
	}
}