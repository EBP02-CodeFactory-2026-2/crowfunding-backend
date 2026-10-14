package com.crowdfunding.backend.exception;

/**
 * DeleteConflictException
 */
public class DeleteConflictException extends RuntimeException{
    public DeleteConflictException (String message){
        super(message);
        
    }
    
}