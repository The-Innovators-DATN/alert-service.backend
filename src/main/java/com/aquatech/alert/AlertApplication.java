package com.aquatech.alert;

import com.aquatech.alert.service.StationAlertService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class AlertApplication {

	private static StationAlertService stationAlertService;

	public static void main(String[] args) {
		SpringApplication.run(AlertApplication.class, args);
	}

}
