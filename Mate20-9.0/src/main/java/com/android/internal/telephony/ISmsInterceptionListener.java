package com.android.internal.telephony;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface ISmsInterceptionListener extends IInterface {

    public static abstract class Stub extends Binder implements ISmsInterceptionListener {
        private static final String DESCRIPTOR = "com.android.internal.telephony.ISmsInterceptionListener";
        static final int TRANSACTION_handleSmsDeliverActionInner = 1;
        static final int TRANSACTION_handleWapPushDeliverActionInner = 2;
        static final int TRANSACTION_sendNumberBlockedRecordInner = 3;

        private static class Proxy implements ISmsInterceptionListener {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public int handleSmsDeliverActionInner(Bundle smsInfo) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (smsInfo != null) {
                        _data.writeInt(1);
                        smsInfo.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int handleWapPushDeliverActionInner(Bundle wapPushInfo) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (wapPushInfo != null) {
                        _data.writeInt(1);
                        wapPushInfo.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean sendNumberBlockedRecordInner(Bundle smsInfo) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _result = true;
                    if (smsInfo != null) {
                        _data.writeInt(1);
                        smsInfo.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() == 0) {
                        _result = false;
                    }
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ISmsInterceptionListener asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof ISmsInterceptionListener)) {
                return new Proxy(obj);
            }
            return (ISmsInterceptionListener) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != 1598968902) {
                Bundle _arg0 = null;
                switch (code) {
                    case 1:
                        data.enforceInterface(DESCRIPTOR);
                        if (data.readInt() != 0) {
                            _arg0 = (Bundle) Bundle.CREATOR.createFromParcel(data);
                        }
                        int _result = handleSmsDeliverActionInner(_arg0);
                        reply.writeNoException();
                        reply.writeInt(_result);
                        return true;
                    case 2:
                        data.enforceInterface(DESCRIPTOR);
                        if (data.readInt() != 0) {
                            _arg0 = (Bundle) Bundle.CREATOR.createFromParcel(data);
                        }
                        int _result2 = handleWapPushDeliverActionInner(_arg0);
                        reply.writeNoException();
                        reply.writeInt(_result2);
                        return true;
                    case 3:
                        data.enforceInterface(DESCRIPTOR);
                        if (data.readInt() != 0) {
                            _arg0 = (Bundle) Bundle.CREATOR.createFromParcel(data);
                        }
                        boolean _result3 = sendNumberBlockedRecordInner(_arg0);
                        reply.writeNoException();
                        reply.writeInt(_result3);
                        return true;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
            } else {
                reply.writeString(DESCRIPTOR);
                return true;
            }
        }
    }

    int handleSmsDeliverActionInner(Bundle bundle) throws RemoteException;

    int handleWapPushDeliverActionInner(Bundle bundle) throws RemoteException;

    boolean sendNumberBlockedRecordInner(Bundle bundle) throws RemoteException;
}