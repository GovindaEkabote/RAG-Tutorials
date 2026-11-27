package com.gen.ai.exception;

public class RagException extends RuntimeException{


    public  RagException(String message){
        super(message);
    }

    public RagException(String message, Throwable cause){
        super(message, cause);
    }
}
