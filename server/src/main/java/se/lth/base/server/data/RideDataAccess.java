package se.lth.base.server.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import Algorithm.Ranker;
import se.lth.base.server.Config;
import se.lth.base.server.database.DataAccess;
import se.lth.base.server.database.Mapper;

/**
 * Contains data access methods for rides
 * @author Sani Mesic
 */
public class RideDataAccess extends DataAccess<Ride> {
	private final RidePersonDataAccess ridePersonDao = new RidePersonDataAccess(Config.instance().getDatabaseDriver());
	private final UserDataAccess userDao = new UserDataAccess(Config.instance().getDatabaseDriver());
	
	private static class RideMapper implements Mapper<Ride> {
		@Override
		public Ride map(ResultSet resultSet) throws SQLException {
			return new Ride(resultSet.getInt("ride_id"), resultSet.getString("departure_location"),
					resultSet.getString("destination"),
					resultSet.getString("departure_time"), 
					resultSet.getString("arrival_time"),
					resultSet.getInt("nbr_seats"),
					resultSet.getInt("driver_id"),
					"Undefined");
		}
	}

	public RideDataAccess(String driverUrl) {
		super(driverUrl, new RideMapper());
	}
	
	/**
	 * 
	 * @param departureLocation
	 * @param destination
	 * @param departureTime
	 * @param arrivalTime
	 * @param nbrSeats
	 * @param driverId
	 * @return new ride containing the input parameters
	 */
	public Ride createRide(String departureLocation, String destination, String departureTime, String arrivalTime, int nbrSeats, int driverId) {
		int rideId = insert("INSERT INTO rides (departure_time, arrival_time, nbr_seats, driver_id, departure_location, destination) VALUES (?,?,?,?,?,?)",
				        departureTime, arrivalTime, nbrSeats, driverId, departureLocation, destination);
		return new Ride(rideId, departureLocation, destination, departureTime, arrivalTime, nbrSeats, driverId, "Undefined");
	}
	
	
	/**
	 * Method to check if user already created a ride that somehow collides with the times
	 * of the given ride
	 * If true, user is busy and should not be able to join the given ride
	 * @param rideId
	 * @param userId
	 * @return
	 * @throws ParseException
	 */
	public boolean userIsBusy(Ride ride, int userId) throws ParseException {
		Date rideATime = ride.arrivalTimeAsDate();
		Date rideDTime = ride.departureTimeAsDate();
		
		List<Ride> userRides = getRides(userId);
		boolean isBusy = false;
		for(Ride r : userRides) {
			Date thisATime = r.arrivalTimeAsDate();
			Date thisDTime = r.departureTimeAsDate();
			//If-conditions from hell :)
			if(rideDTime.before(thisDTime) && rideATime.after(thisDTime) && rideATime.before(thisATime) 
					|| rideDTime.after(thisDTime) && rideATime.before(thisATime)
					|| rideDTime.after(thisDTime) && rideDTime.before(thisATime) && rideATime.after(thisATime)
					|| rideDTime.before(thisDTime) && rideATime.after(thisATime)) {
				isBusy = true;
			}
		}
		return isBusy;
	}
	
	
	/**
	 * 
	 * @return all rides currently in the system
	 */
	public List<Ride> getAllRides(){
		return query("SELECT * FROM rides JOIN locations ON rides.departure_location = locations.location_name");
	}
	
	
	/**
	 * 
	 * @param rideId
	 * @return ride with specified ride id
	 */
	public Ride getRide(int rideId) {
		return queryFirst("SELECT * FROM rides WHERE ride_id = ?", rideId);
	}
		
	/**
	 * @param userId
	 * @return all rides connected to one user id, i.e. where the specific user is either a passenger or driver
	 *
	 */
	public List<Ride> getRides(int userId) {
		List<RidePerson> ridesAsPassenger = ridePersonDao.getRidesAsPassenger(userId);
		
		// Insert rides as driver directly, set role to driver
		List<Ride> rides =  query("SELECT * FROM rides WHERE driver_id = ?", userId);
		for (int i = 0; i < rides.size(); i++) {
			rides.get(i).setRole("Driver");
		}
		// Insert passengers
		for (int i = 0; i < ridesAsPassenger.size(); i++) {
			Ride ride = queryFirst("SELECT * FROM rides WHERE ride_id = ?", ridesAsPassenger.get(i).getRideId());
			ride.setRole("Passenger");
			rides.add(ride);
		}
		return rides;
	}
	
	
	/**
	 * 
	 * @param rideId
	 * @return true if ride is deleted, false otherwise
	 */
	public boolean deleteRide(int rideId) {
        return execute("DELETE FROM rides WHERE ride_id = ?", rideId) > 0;
    }
	
	/**
	 * 
	 * @param rideId
	 * @return true if seats are available, false otherwise
	 */
	public boolean checkIfEmptySeats(int rideId) {
		Ride ride = queryFirst("SELECT * FROM rides WHERE ride_id = ?", rideId);
		return ride.getCarSize() > 0;
	}
	
	public List<Ride> getRidesAsDriver(int userId){
		return query("SELECT * FROM rides WHERE driver_id = ?", userId);
	}
	
	
	/**
	 * 
	 * adds user to a ride as a passenger
	 * @param rideId
	 * @param userId
	 * 
	 * @throws ParseException 
	 */

	public Ride addUserToRide(int rideId, int userId) throws ParseException{
		//Checks if user is booked during that time period
		 
		// Decrements nbr_seats by 1
		execute("UPDATE rides SET nbr_seats = nbr_seats - 1 WHERE ride_id = ?", rideId);
		// Inserts passenger to ride_passengers table
		execute("INSERT INTO ride_passengers (ride_id, user_id) VALUES (?,?)", rideId, userId);
		
		Ride ride = getRide(rideId);
		User passenger = userDao.getUser(userId);
		ride.addTraveler(passenger);
		
		return ride;
	}

	/**
	 * Method to check if user already signed up to a ride that somehow collides with the times
	 * of the given ride
	 * If true, user is busy and should not be able to join the given ride
	 * @param rideId
	 * @param userId
	 * @return
	 * @throws ParseException
	 */
	public boolean userIsBusy(int rideId, int userId) throws ParseException {
		Ride ride = queryFirst("SELECT * FROM rides WHERE ride_id = ?", rideId);
		Date rideATime = ride.arrivalTimeAsDate();
		Date rideDTime = ride.departureTimeAsDate();
		
		List<Ride> userRides = getRides(userId);
		boolean isBusy = false;
		for(Ride r : userRides) {
			Date thisATime = r.arrivalTimeAsDate();
			Date thisDTime = r.departureTimeAsDate();
			//If-conditions from hell :)
			if(rideDTime.before(thisDTime) && rideATime.after(thisDTime) && rideATime.before(thisATime) 
					|| rideDTime.after(thisDTime) && rideATime.before(thisATime)
					|| rideDTime.after(thisDTime) && rideDTime.before(thisATime) && rideATime.after(thisATime)
					|| rideDTime.before(thisDTime) && rideATime.after(thisATime)) {
				isBusy = true;
			}
		}
		return isBusy;
	}
	
	/**
	 * 
	 * removes user from a ride as a passenger
	 * @param rideId
	 * @param userId
	 * 
	 */
	public Ride removeUserFromRide(int rideId, int userId) {
		
		// Increments nbr_seats by 1
		execute("UPDATE rides SET nbr_seats = nbr_seats + 1 WHERE ride_id = ?", rideId);
		
		// Removes passenger to ride_passengers table
		execute("DELETE FROM ride_passengers WHERE ride_id = ? AND user_id = ?", rideId, userId);
		
		Ride ride = getRide(rideId);
		User passenger = userDao.getUser(userId);
		ride.removeTraveler(passenger);
		
		return ride;
	}
	
	/**
	 * Sorts out the relevant rides for the user based on time and arrival location
	 * @param aLocation
	 * @param dLocation
	 * @param arrivalTime
	 * @param departureTime
	 * @return
	 * @throws ParseException
	 */
	public List<Ride> getRelevantRides(String aLocation, String dLocation,
    		String arrivalTime, String departureTime, int userId) throws ParseException {
		
		Ride userRide = new Ride(dLocation, aLocation, departureTime, arrivalTime);
		List<Ride> allRides = getAllRides();
		Ranker ranker = new Ranker(allRides, userRide, userId);
		return ranker.rank();
	}


}
