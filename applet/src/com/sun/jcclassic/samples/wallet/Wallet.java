package com.sun.jcclassic.samples.wallet;

/*
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

/*
 * @(#)Wallet.java	1.11 06/01/03
 */
import javacard.framework.*;

public class Wallet extends Applet {

    /* constants declaration */

    // code of CLA byte in the command APDU header
    final static byte Wallet_CLA =(byte)0x80;

    // codes of INS byte in the command APDU header
    final static byte VERIFY = (byte) 0x20;
    final static byte CREDIT = (byte) 0x30;
    final static byte DEBIT = (byte) 0x40;
    final static byte GET_BALANCE = (byte) 0x50;

    // maximum balance
    final static short MAX_BALANCE = 0x2710;
    
    // maximum loyalty points
    final static short MAX_LOYALTY_POINTS = 0x12C;
    
    final static short MONEY_FOR_POINT = 0xA;
    
    // maximum transaction amount
    final static short MAX_TRANSACTION_AMOUNT = 0x3E9;

    // maximum number of incorrect tries before the
    // PIN is blocked
    final static byte PIN_TRY_LIMIT =(byte)0x03;
    // maximum size PIN
    final static byte MAX_PIN_SIZE =(byte)0x08;

    // signal that the PIN verification failed
    final static short SW_VERIFICATION_FAILED = 0x6300;
    // signal the the PIN validation is required
    // for a credit or a debit transaction
    final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;
    // signal invalid transaction amount
    // amount > MAX_TRANSACTION_AMOUNT or amount < 0
    final static short SW_INVALID_TRANSACTION_AMOUNT = 0x6A83;

    // signal that the balance exceed the maximum
    final static short SW_EXCEED_MAXIMUM_BALANCE = 0x6A84;
    // signal the the balance becomes negative
    final static short SW_NEGATIVE_BALANCE = 0x6A85;
    
    final static short SW_INSUFICIENT_LOYALTY_POINTS = 0x6A86;
    
    final static short SW_INSUFICIENT_FUNDS = 0x6A87;
    
    final static short SW_DEBIT_TOO_LARGE = 0x6A88;
    
    /* instance variables declaration */
    OwnerPIN pin;
    short balance;
    short loyaltyPoints = 0;

    private Wallet (byte[] bArray,short bOffset,byte bLength) {

        // It is good programming practice to allocate
        // all the memory that an applet needs during
        // its lifetime inside the constructor
        pin = new OwnerPIN(PIN_TRY_LIMIT,   MAX_PIN_SIZE);

        byte iLen = bArray[bOffset]; // aid length
        bOffset = (short) (bOffset+iLen+1);
        byte cLen = bArray[bOffset]; // info length
        bOffset = (short) (bOffset+cLen+1);
        byte aLen = bArray[bOffset]; // applet data length

        // The installation parameters contain the PIN
        // initialization value
        pin.update(bArray, (short)(bOffset+1), aLen);
        register();

    } // end of the constructor

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        // create a Wallet applet instance
        new Wallet(bArray, bOffset, bLength);
    } // end of install method

    public boolean select() {

        // The applet declines to be selected
        // if the pin is blocked.
        if ( pin.getTriesRemaining() == 0 )
            return false;

        return true;

    }// end of select method

    public void deselect() {

        // reset the pin value
        pin.reset();

    }

    public void process(APDU apdu) {

        // APDU object carries a byte array (buffer) to
        // transfer incoming and outgoing APDU header
        // and data bytes between card and CAD

        // At this point, only the first header bytes
        // [CLA, INS, P1, P2, P3] are available in
        // the APDU buffer.
        // The interface javacard.framework.ISO7816
        // declares constants to denote the offset of
        // these bytes in the APDU buffer

        byte[] buffer = apdu.getBuffer();
        // check SELECT APDU command

        if (apdu.isISOInterindustryCLA()) {
            if (buffer[ISO7816.OFFSET_INS] == (byte)(0xA4)) {
                return;
            } else {
                ISOException.throwIt (ISO7816.SW_CLA_NOT_SUPPORTED);
            }
        }

        // verify the reset of commands have the
        // correct CLA byte, which specifies the
        // command structure
        if (buffer[ISO7816.OFFSET_CLA] != Wallet_CLA)
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);

        switch (buffer[ISO7816.OFFSET_INS]) {
            case GET_BALANCE:
                getBalance(apdu);
                return;
            case DEBIT:
                debit(apdu);
                return;
            case CREDIT:
                credit(apdu);
                return;
            case VERIFY:
                verify(apdu);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }

    }   // end of process method

    private void credit(APDU apdu) {

        // access authentication
        if ( ! pin.isValidated() )
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);

        byte[] buffer = apdu.getBuffer();

        // Lc byte denotes the number of bytes in the
        // data field of the command APDU
        byte numBytes = buffer[ISO7816.OFFSET_LC];

        // indicate that this APDU has incoming data
        // and receive data starting from the offset
        // ISO7816.OFFSET_CDATA following the 5 header
        // bytes.
        byte byteRead =
                (byte)(apdu.setIncomingAndReceive());

        // it is an error if the number of data bytes
        // read does not match the number in Lc byte
        if ( ( numBytes != 2 ) || (byteRead != 2) )
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        // get the credit amount
        byte b1 = buffer[ISO7816.OFFSET_CDATA];
        byte b2 = buffer[ISO7816.OFFSET_CDATA + 1];
        
        short creditAmount = (short) (b1<<8 | b2 & 0xFF);

        // check the credit amount
        if ( ( creditAmount > MAX_TRANSACTION_AMOUNT)
                || ( creditAmount < 0 ) )
            ISOException.throwIt(SW_INVALID_TRANSACTION_AMOUNT);

        // check the new balance
        if ( (short)( balance + creditAmount)  > MAX_BALANCE )
            ISOException.throwIt(SW_EXCEED_MAXIMUM_BALANCE);

        // credit the amount
        balance = (short)(balance + creditAmount);

    } // end of deposit method

    private void debit(APDU apdu) {

        // access authentication
        if ( ! pin.isValidated() )
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);

        byte[] buffer = apdu.getBuffer();

        byte numBytes =
                (byte)(buffer[ISO7816.OFFSET_LC]);

        byte byteRead =
                (byte)(apdu.setIncomingAndReceive());

        if ( ( numBytes != 4 ) || (byteRead != 4) )
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        // get debit amount
        byte debitAmount1 = buffer[ISO7816.OFFSET_CDATA];
        byte debitAmount2 = buffer[ISO7816.OFFSET_CDATA + 1];
        
        short debitAmount = (short) (debitAmount1<<8 | debitAmount2 & 0xFF);
        

        byte loyaltyPointsAmount1 = buffer[ISO7816.OFFSET_CDATA + 2];
        byte loyaltyPointsAmount2 = buffer[ISO7816.OFFSET_CDATA + 3];
        short paidInLoyaltyPoints = (short) (loyaltyPointsAmount1 << 8 | loyaltyPointsAmount2 & 0xFF);
        
        makePayment(debitAmount, paidInLoyaltyPoints);

    } // end of debit method

    private void getBalance(APDU apdu) {
        // access authentication
        if ( ! pin.isValidated() )
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);

        byte[] buffer = apdu.getBuffer();
        
        // Lc byte denotes the number of bytes in the
        // data field of the command APDU
        byte numBytes = buffer[ISO7816.OFFSET_LC];

        // indicate that this APDU has incoming data
        // and receive data starting from the offset
        // ISO7816.OFFSET_CDATA following the 5 header
        // bytes.
        byte byteRead =
                (byte)(apdu.setIncomingAndReceive());

        // it is an error if the number of data bytes
        // read does not match the number in Lc byte
        if ( ( numBytes != 1 ) || (byteRead != 1) )
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        // get the credit amount
        byte option = buffer[ISO7816.OFFSET_CDATA];

        // inform system that the applet has finished
        // processing the command and the system should
        // now prepare to construct a response APDU
        // which contains data field
        short le = apdu.setOutgoing();

        if ( le < 4 )
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        //informs the CAD the actual number of bytes
        //returned
        apdu.setOutgoingLength((byte)4);
        
        switch(option) {
        case 0://money only
        	 buffer[0] = (byte)(balance >> 8);
             buffer[1] = (byte)(balance & 0xFF);
             buffer[2] = 0;
             buffer[3] = 0;
             // send the 2-byte balance at the offset
             // 0 in the apdu buffer
             apdu.sendBytes((short)0, (short)4);
             break;
        case 1://loyalty points only
            buffer[0] = 0;
            buffer[1] = 0;
            buffer[2] = (byte)(loyaltyPoints >> 8);
            buffer[3] = (byte)(loyaltyPoints & 0xFF);
	         // send the 2-byte balance at the offset
	         // 0 in the apdu buffer
	         apdu.sendBytes((short)0, (short)4);
	         break;
        case 2://both
            // move the balance data into the APDU buffer
            // starting at the offset 0
            buffer[0] = (byte)(balance >> 8);
            buffer[1] = (byte)(balance & 0xFF);
            buffer[2] = (byte)(loyaltyPoints >> 8);
            buffer[3] = (byte)(loyaltyPoints & 0xFF);

            // send the 2-byte balance at the offset
            // 0 in the apdu buffer
            apdu.sendBytes((short)0, (short)4);
            break;
         default:
        	 ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        	 break;
        }
    } // end of getBalance method

    private void verify(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        // retrieve the PIN data for validation.
        byte byteRead = (byte)(apdu.setIncomingAndReceive());

        // check pin
        // the PIN data is read into the APDU buffer
        // at the offset ISO7816.OFFSET_CDATA
        // the PIN data length = byteRead
        if ( pin.check(buffer, ISO7816.OFFSET_CDATA,
                byteRead) == false )
            ISOException.throwIt(SW_VERIFICATION_FAILED);

    } // end of validate method
    
    private void makePayment(short debitAmount, short paidInLoyaltyPoints){
    	if(debitAmount > MAX_TRANSACTION_AMOUNT) {
            ISOException.throwIt(SW_DEBIT_TOO_LARGE);
    	}
    	
    	short extraPoints = addPoints(debitAmount, paidInLoyaltyPoints);
    	subtractMoney(debitAmount, paidInLoyaltyPoints, extraPoints);
    }
    
    private short addPoints(short debitAmount, short paidInLoyaltyPoints) {
    	short pointsFromDebitAmount = (short) (debitAmount / 10);
    	
    	short pointsAfterDifference = (short) (pointsFromDebitAmount - paidInLoyaltyPoints);
    	
    	if((short) (this.loyaltyPoints + pointsAfterDifference) < (short) 0){
            ISOException.throwIt(SW_INSUFICIENT_LOYALTY_POINTS);
    	}
    	
    	
    	if((short) (this.loyaltyPoints + pointsAfterDifference) > MAX_LOYALTY_POINTS ){
            this.loyaltyPoints = MAX_LOYALTY_POINTS;
            return (short) (this.loyaltyPoints + pointsAfterDifference - MAX_LOYALTY_POINTS);
    	} else {
    		this.loyaltyPoints += pointsAfterDifference;
    		return 0;
    	}
    }
    
    private void subtractMoney(short debitAmount, short paidInLoyaltyPoints, short extraPoints){
    	short pointsFromDebitAmount = (short) ( debitAmount / MONEY_FOR_POINT);
    	
    	short moneyAndLPWithoutCurrTransaction = 
    			(short) (this.loyaltyPoints + this.balance - pointsFromDebitAmount);
    	
    	
    	if(debitAmount <= this.balance) {//can afford with current money{
    		if(extraPoints <= debitAmount)
    			this.balance -= (debitAmount - extraPoints);
    	}
    	// cant afford with current money
    	else {
    		if(debitAmount <= (short) (this.loyaltyPoints))
    			//can afford with points alone
    			payWithLoyaltyPoints(debitAmount, extraPoints);
    		
    		//can afford with points AND money combined
    		else if(debitAmount <= moneyAndLPWithoutCurrTransaction)
    			payWithMoneyAndLP(debitAmount, extraPoints);
    		
    		//cant afford at all
    		else rollback(debitAmount, paidInLoyaltyPoints, extraPoints);
    	}
    }
    
    private void rollback(short debitAmount, 
    		short paidInLoyaltyPoints, 
    		short extraPoints) {
		rollbackLoyaltyPoints(debitAmount, paidInLoyaltyPoints, extraPoints);
        ISOException.throwIt(SW_INSUFICIENT_FUNDS);
    }
    
    private void payWithLoyaltyPoints(short debitAmount, short extraPoints) {
		if(extraPoints <= debitAmount)
			this.loyaltyPoints -= (debitAmount - extraPoints);
    }
    
    private void payWithMoneyAndLP(short debitAmount, short extraPoints){
		short amountLeftToPay = (short) (debitAmount - extraPoints - this.loyaltyPoints);
		this.loyaltyPoints = 0;
		this.balance -= amountLeftToPay;
    }
    
    private void rollbackLoyaltyPoints(short debitAmount, 
    		short paidInLoyaltyPoints, 
    		short extraPoints) {
    	short amountToRollback = (short) (debitAmount / MONEY_FOR_POINT);
    	short pointsAfterDifference = (short) (amountToRollback - paidInLoyaltyPoints);

    	this.loyaltyPoints -= (pointsAfterDifference - extraPoints);
    }
} // end of class Wallet