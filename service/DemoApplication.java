package com.example.demo;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class DemoApplication {


//test
	@GetMapping("/")
	public String home() {
		try{

		}catch(Exception e){
			logger.error("Error :",e);
		}
		try{

		}catch(Exception e){
			logger.error("Error :",e);
		}
		System.out.print("The addition of ");
			//System.out.print("The addition of ");
   
		return "Spring is here!";

	}

	public static void main(String[] args) {
		            System.out.println("Login failed.");

		SpringApplication.run(DemoApplication.class, args);
	}
}
