package org.ccnx.ccn.test.protocol;


import java.security.InvalidParameterException;
import java.util.ArrayList;

import org.ccnx.ccn.KeyManager;
import org.ccnx.ccn.impl.security.crypto.CCNDigestHelper;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.protocol.BloomFilter;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.ExcludeComponent;
import org.ccnx.ccn.protocol.ExcludeFilter;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.PublisherID;
import org.ccnx.ccn.protocol.Signature;
import org.ccnx.ccn.test.impl.encoding.XMLEncodableTester;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;



public class InterestTest {
	
	public static String testName = "/test/parc/home/smetters/interestingData.txt/v/5";
	public static ContentName tcn = null;
	public static PublisherID pubID = null;
	
	private byte [] bloomSeed = "burp".getBytes();
	private ExcludeFilter ef = null;
	
	private String [] bloomTestValues = {
            "one", "two", "three", "four",
            "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve",
            "thirteen"
      	};

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		
		byte [] testID = CCNDigestHelper.digest(testName.getBytes());
		
		tcn = ContentName.fromURI(testName);
		pubID = new PublisherID(testID,PublisherID.PublisherType.ISSUER_KEY);
	}

	@Before
	public void setUp() throws Exception {
	}
	
	private void excludeSetup() {
		BloomFilter bf1 = new BloomFilter(13, bloomSeed);
		ExcludeComponent e1 = new ExcludeComponent("aaa".getBytes());
		ExcludeComponent e2 = new ExcludeComponent("zzzzzzzz".getBytes());
		
		try {
			ArrayList<ExcludeFilter.Element>te = new ArrayList<ExcludeFilter.Element>(2);
			te.add(e2);
			te.add(e1);
			new ExcludeFilter(te);
			Assert.fail("Out of order exclude filter succeeded");
		} catch (InvalidParameterException ipe) {}
		
		for (String value : bloomTestValues) {
			bf1.insert(value.getBytes());
		}
		ArrayList<ExcludeFilter.Element>excludes = new ArrayList<ExcludeFilter.Element>(2);
		excludes.add(e1);
		excludes.add(bf1);
		excludes.add(e2);
		ef = new ExcludeFilter(excludes);
	}

	@Test
	public void testSimpleInterest() {
		Interest plain = new Interest(tcn);
		Interest plainDec = new Interest();
		Interest plainBDec = new Interest();
		XMLEncodableTester.encodeDecodeTest("PlainInterest", plain, plainDec, plainBDec);

		Interest nplain = new Interest(tcn,pubID);
		Interest nplainDec = new Interest();
		Interest nplainBDec = new Interest();
		XMLEncodableTester.encodeDecodeTest("FancyInterest", nplain, nplainDec, nplainBDec);
		
		Interest opPlain = new Interest(tcn);
		opPlain.childSelector(Interest.CHILD_SELECTOR_RIGHT);
		Interest opPlainDec = new Interest();
		Interest opPlainBDec = new Interest();
		XMLEncodableTester.encodeDecodeTest("PreferenceInterest", opPlain, opPlainDec, opPlainBDec);
		
		Interest opMSC = new Interest(tcn);
		opMSC.maxSuffixComponents(3);
		Interest opMSCDec = new Interest();
		Interest opMSCBDec = new Interest();
		XMLEncodableTester.encodeDecodeTest("MaxSuffixComponentsInterest", opMSC, opMSCDec, opMSCBDec);	

		Interest opMinSC = new Interest(tcn);
		opMinSC.minSuffixComponents(3);
		Interest opMinSCDec = new Interest();
		Interest opMinSCBDec = new Interest();
		XMLEncodableTester.encodeDecodeTest("MinSuffixComponentsInterest", opMinSC, opMinSCDec, opMinSCBDec);
	}
	
	@Test
	public void testProfileInterests() throws Exception {
		// Should test the interests used for segments (getLower) as well.
		Interest lv = 
			VersioningProfile.latestVersionInterest(
					ContentName.fromNative("/test/InterestTest/testProfileInterests"), 
					null, KeyManager.getDefaultKeyManager().getDefaultKeyID());
		Interest lvDec = new Interest();
		Interest lvBDec = new Interest();
		XMLEncodableTester.encodeDecodeTest("LatestVersionInterest", lv, lvDec, lvBDec);
		Interest lvs = 
			VersioningProfile.latestVersionInterest(
					ContentName.fromNative("/test/InterestTest/testProfileInterests"), 
					2, KeyManager.getDefaultKeyManager().getDefaultKeyID());
		Interest lvsDec = new Interest();
		Interest lvsBDec = new Interest();
		XMLEncodableTester.encodeDecodeTest("LatestVersionInterest - Short", lvs, lvsDec, lvsBDec);
	}
		
	@Test
	public void testExcludeFilter() {
		excludeSetup();
		
		Interest exPlain = new Interest(tcn);
		exPlain.excludeFilter(ef);
		Interest exPlainDec = new Interest();
		Interest exPlainBDec = new Interest();
		XMLEncodableTester.encodeDecodeTest("ExcludeInterest", exPlain, exPlainDec, exPlainBDec);
	}
	
	@Test
	public void testMatch() {
		// paul r Comment - should really test more comprehensively
		// For now just do this to test the exclude matching
		excludeSetup();
		try {
			Interest interest = new Interest("/paul");
			interest.excludeFilter(ef);
			Assert.assertTrue(interest.matches(ContentName.fromNative("/paul/car"), null));
			Assert.assertFalse(interest.matches(ContentName.fromNative("/paul/zzzzzzzz"), null));
			for (String value : bloomTestValues) {
				String completeName = "/paul/" + value;
				Assert.assertFalse(interest.matches(ContentName.fromNative(completeName), null));
			}
		} catch (MalformedContentNameStringException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testMatchDigest() throws MalformedContentNameStringException {
		ContentName name = ContentName.fromNative("/paul");
		byte [] content = "hello".getBytes();
		ContentObject co = new ContentObject(name,null,content,(Signature)null);
		byte [] digest = co.contentDigest();
		Interest interest = new Interest(ContentName.fromNative(name, digest));
		Assert.assertTrue(interest.matches(co));
		interest = new Interest(ContentName.fromNative(name, "simon"));
		Assert.assertFalse(interest.matches(co));
	}
}