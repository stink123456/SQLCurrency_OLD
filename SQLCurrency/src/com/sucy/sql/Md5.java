package com.sucy.sql;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Md5 {
	public static String getHash(String pass) {
		if(pass == null) return null;
		String md5 = null;
			try {
				// Create MessageDigest object for MD5
				MessageDigest digest = MessageDigest.getInstance("MD5");

				// Update input string in message digest
				digest.update(pass.getBytes(), 0, pass.length());

				// Converts message digest value in base 16 (hex)
				md5 = new BigInteger(1, digest.digest()).toString(16);

			} catch (NoSuchAlgorithmException e) {

				e.printStackTrace();
			}
			return md5;
		}
}