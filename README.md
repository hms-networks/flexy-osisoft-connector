# flexy-osisoft

## About

This demo reads multiple tag values from an eWON Flexy IO Server and POSTs them to an OSIsoft PI Server.

## Certificates

The Flexy by default only allows HTTPS connections with servers that have certificates verified by a trusted certificate authority.  To enable a HTTPS connections with a server that has a self signed certificate the Flexy must have a copy of that certificate.  Follow the steps outlined in this section to create and install a new certificate.

### Generate the certificate 

Here is an example of generating a certificate using openssl.

Generate a certificate and a private key good for 5 years.
```
openssl req -newkey rsa:2048 -nodes -keyout key.pem -x509 -days 1825 -out certificate.crt
```
Answer the prompts, making sure that your set the "Common Name" to the IP Address of your server.

Combine the certificate and private key into a .p12 file
```
openssl pkcs12 -inkey key.pem -in certificate.crt -export -out certificate.p12
```

You will be prompted to enter an export password, the again to verify.  Remember this password.

### Install the certificate on server
In Windows, right click on your certificate.crt file and click Install Certificate.  Follow the prompts and place the certificate in the "Trusted Root Certification Authorities" store.  When you click finish a security prompt will warn you that it cannot validate the certificate's origin and ask if you want to install this certificate, click yes. 

Run mmc.exe.  Click "File"->"Add/Remove Snap-in...".  From the "Availbile snap-ins" add "Certificates" to the "Selected snap-ins", set the permissions to "Computer account" when prompted, then select your local computer.  Click "Finish", then "Ok".  Expand "Certificates (Local Computer)", then expand "Personal", then expand and select "Certificates". Click "Action"->"All Tasks"->"Import". Click "Next" then "Browse".  The the file open window select the file extension to be "Personal Information Exchange (*.pfx;*.p12)".  Find and select your "certificate.p12" file, then click open then next.  You will now be prompted to enter in your export password from when you created your certificate.p12 file, after doing so click next.  Place the certificate in the "Personal" certificate store, then click "Next" then "Finish".  You should be prompted that the import was successful, click "OK".

Run "PI Web API Admin Utility". Configure the server normally.  When you get to the "Certificate" setup page click "Change".  You may be prompted that a certificate binding is already configured, click "Yes" to "Do you still want to change the certificate?".  Select the certificate you created from the list and click "OK".  Continue with the rest of the configuration normally.

### Transfer certificate to Flexy

The certificate must be placed somewhere in the /usr directory of the Flexy. The certificate can be transfered to the Flexy using FTP. In this example a Certificates directory is used.  If another directory is to be used the eWONCertificatePath string in OSIsoft_DemoMain.java must be updated.
```
// Path to the directory containing your OSIsoft server's certificate 
static String eWONCertificatePath = "/usr/Certificates";
```