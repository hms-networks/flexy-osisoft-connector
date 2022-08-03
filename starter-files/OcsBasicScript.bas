Rem --- Ewon start section: Cyclic Section
eWON_cyclic_section:
Rem --- Ewon user (start)

Rem --- Ewon user (end)
End
Rem --- Ewon end section: Cyclic Section
Rem --- Ewon start section: Init Section
eWON_init_section:
Rem --- Ewon user (start)
// 30 Minutes in seconds
Seconds$ = 1800
TSET 1, Seconds$
ONTIMER 1, "@updateToken()"
END

Function updateToken()
  URL$ = "https://dat-b.osisoft.com/identity/connect/token"
  Header$ = ""
  ClientId$ = "xxxxxx"
  ClientSecret$ = "yyyyyy"
  DataToSend$ = "client_id=" + ClientId$ + "&client_secret=" + ClientSecret$ + "&grant_type=client_credentials"
  File$ = "/usr/token.json"
  REQUESTHTTPX URL$, "POST", Header$, DataToSend$, "", File$

  // change tag value here to mark as set
  tokenReq@ = (tokenReq@ + 1)
ENDFN