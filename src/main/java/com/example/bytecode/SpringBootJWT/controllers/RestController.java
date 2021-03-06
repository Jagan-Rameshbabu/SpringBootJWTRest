package com.example.bytecode.SpringBootJWT.controllers;

import com.example.bytecode.SpringBootJWT.entities.Operation;
import com.example.bytecode.SpringBootJWT.entities.User;
import com.example.bytecode.SpringBootJWT.services.LoginService;
import com.example.bytecode.SpringBootJWT.services.OperationService;
import com.example.bytecode.SpringBootJWT.utils.UserNotLoggedException;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.POST;



@org.springframework.web.bind.annotation.RestController
public class RestController {

    private static final Logger log = LoggerFactory.getLogger(RestController.class);


    @Resource(name = "loginService")        //concrete class will be injected by Spring
    LoginService loginService;

    @Autowired
    OperationService operationService;      //concrete class will be injected by Spring



    /**
     * return a JSON message with the session token and the message of success
     * @param id passed as Request Param via POST form submit
     * @param pwd      passed as Request Param via POST form submit
     * @return ResponseEntity with jwt and message
     */
    @RequestMapping(value = "/login", method = POST)
    public ResponseEntity<Object> loginUser(@RequestParam(value ="id") String id, @RequestParam(value="password") String pwd){
        try {
            Optional<User> userr = loginService.getUserFromDbAndVerifyPassword(id, pwd);      //verify the presence into the database
            if (userr.isPresent()) {
                User user = userr.get();                                  //get the User from the optional got from the DB
                String jwt = loginService.createJwt(user.getId(), user.getUsername(), user.getPermission(), new Date());
                //set the jwt token into the header of response
                return ResponseEntity.status(HttpStatus.OK).header("jwt", jwt).body(new JsonResponseBody(HttpStatus.OK.value(),"Success! User logged in." + jwt));
            }
        }catch(UserNotLoggedException e1){ //thrown by loginService#getUserFromDbAndVerifyPassword
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Login failed! Wrong credentials. " + e1.toString()));
        }catch(UnsupportedEncodingException e2){  //thrown by loginService#createJwt
           return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Login failed! Encoding permission token error. " + e2.toString()));
        }
        //send response to client
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Login failed! No corrispondence found into the database of users."));
    }



    /**
     * it returns a JSON with the list of operation of the specified account (if the jwt is found in header request and it's valid)
     * @param request passed automatically
     * @param account passed as a REST path variable
     * @return JSON of the List of operations in an account
     */
    @RequestMapping("/operations/account/{account}")
    public ResponseEntity<Object> fetchAllOperationsPerAccount(HttpServletRequest request, @PathVariable(name = "account") String account){
        try {
            Map<String, Object> userData = loginService.verifyJwtAndGetData(request); //not used but launches Exception if something wrong
            return ResponseEntity.status(HttpStatus.OK).body(new JsonResponseBody(HttpStatus.OK.value(), operationService.getAllOperationPerAccount(account)));
        }catch(UnsupportedEncodingException e1){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JsonResponseBody(HttpStatus.BAD_REQUEST.value(),"Errore! " + e1.toString()));
        }catch(ExpiredJwtException e2) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new JsonResponseBody(HttpStatus.GATEWAY_TIMEOUT.value(),"Error! Access token expired. " + e2.toString()));
        }catch(UserNotLoggedException e3){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Error! You must login first. " + e3.toString()));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Error! " + e.toString()));
        }
    }



    /**
     * it returns a JSON with the List of accounts for the logged user
     * @param request passed automatically
     * @return JSON of the List of Accounts for User
     */
    @RequestMapping(value = "/accounts/user", method = POST)
    public ResponseEntity fetchAllAccountsPerUser(HttpServletRequest request){
        try {
            Map<String, Object> userData = loginService.verifyJwtAndGetData(request); //decode jwt and get user data
            return ResponseEntity.status(HttpStatus.OK).body(new JsonResponseBody(HttpStatus.OK.value(), operationService.getAllAccountsPerUser((String) userData.get("subject"))));
        }catch(UnsupportedEncodingException e1){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JsonResponseBody(HttpStatus.BAD_REQUEST.value(), "Error! Token not recognized" + e1.toString()));
        }catch(ExpiredJwtException e2) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new JsonResponseBody(HttpStatus.GATEWAY_TIMEOUT.value(),"Error! Session Expired."));
        }catch(UserNotLoggedException e3){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Error! You must login first. " + e3.toString()));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Error!" + e.toString()));
        }
    }


    /**
     * it saves the inserted operation into the Database if the User is valid and logged and session is not expired
     * @param request passed automatically
     * @param operation passed with a form post submit with the automatic binding of the Operation object
     * @param bindingResult passed automatically to check the correctness of Operation attributes with JSR-303 validation
     * @return JSON confirming the saved Operation
     */
    @RequestMapping(value = "/operations/add", method=POST)
    public ResponseEntity addOperation(HttpServletRequest request, @Valid Operation operation, BindingResult bindingResult){
        if(bindingResult.hasErrors()){                           //automatic JSR303 validation
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Error! Invalid format of data."));
        }
        try {                                                // let's verify if it's valid
           Map<String, Object> userData = loginService.verifyJwtAndGetData(request); //decode jwt and get user data
           return ResponseEntity.accepted().body(new JsonResponseBody(HttpStatus.OK.value(), operationService.saveOperation(operation)));
        }catch(UnsupportedEncodingException e1){
           return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JsonResponseBody(HttpStatus.BAD_REQUEST.value(),"Error! Token not recognized" + e1.toString()));
        }catch(ExpiredJwtException e2){
           return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new JsonResponseBody(HttpStatus.GATEWAY_TIMEOUT.value(),"Error! Session Expired."));
        }catch(UserNotLoggedException e3){
           return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Error! You must login first. " + e3.toString()));
        }catch(Exception e){
           return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new JsonResponseBody(HttpStatus.FORBIDDEN.value(),"Error!" + e.toString()));
        }
    }


    /**
     * inner class used as the Object tied into the Body of the ResponseEntity.
     * It's important to have this Object because it is composed of server response code and response object.
     * Then, JACKSON LIBRARY automatically convert this JsonResponseBody Object into a JSON response.
     */
    @AllArgsConstructor
    public class JsonResponseBody{
        @Getter @Setter
        private int server;
        @Getter @Setter
        private Object response;
    }


}
