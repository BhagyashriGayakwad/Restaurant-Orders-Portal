package com.nt.restaurant.microservice.serviceimpl;

import com.nt.restaurant.microservice.dto.CommonResponse;
import com.nt.restaurant.microservice.dto.FoodItemInDTO;
import com.nt.restaurant.microservice.dto.FoodItemOutDTO;
import com.nt.restaurant.microservice.dto.FoodItemUpdateInDTO;
import com.nt.restaurant.microservice.dtoconvertion.FoodItemDtoConverter;
import com.nt.restaurant.microservice.entities.FoodCategory;
import com.nt.restaurant.microservice.entities.FoodItem;
import com.nt.restaurant.microservice.entities.Restaurant;
import com.nt.restaurant.microservice.exception.InvalidRequestException;
import com.nt.restaurant.microservice.exception.ResourceAlreadyExistException;
import com.nt.restaurant.microservice.exception.ResourceNotFoundException;
import com.nt.restaurant.microservice.repository.FoodCategoryRepository;
import com.nt.restaurant.microservice.repository.FoodItemRepository;
import com.nt.restaurant.microservice.repository.RestaurantRepository;
import com.nt.restaurant.microservice.service.FoodItemService;
import com.nt.restaurant.microservice.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of the FoodItemService interface that provides operations for managing food items.
 */
@Service
public class FoodItemServiceImpl implements FoodItemService {

  /**
   * Logger instance for logging events within the service.
   */
  private static final Logger LOGGER = LogManager.getLogger(FoodItemServiceImpl.class);

  /**
   * Repository for accessing food categories in the database.
   */
  @Autowired
  private FoodCategoryRepository foodCategoryRepository;

  /**
   * Repository for accessing restaurants in the database.
   */
  @Autowired
  private RestaurantRepository restaurantRepository;

  /**
   * Repository for accessing food items in the database.
   */
  @Autowired
  private FoodItemRepository foodItemRepository;

  /**
   * Adds a new food item to the system.
   *
   * @param foodItemInDTO The DTO containing the details of the food item to be added.
   * @param image         The image of the food item to be uploaded.
   * @return A response indicating the outcome of the operation.
   * @throws ResourceNotFoundException If the restaurant or food category is not found, or if the food item already exists.
   */
  @Override
  public CommonResponse addFoodItem(final FoodItemInDTO foodItemInDTO, final MultipartFile image) {
    LOGGER.info("Attempting to add food item: {}", foodItemInDTO.getFoodItemName());

    LOGGER.debug("Fetching restaurant with ID: {}", foodItemInDTO.getRestaurantId());
    Optional<Restaurant> restaurant = restaurantRepository.findById(foodItemInDTO.getRestaurantId());
    if (!restaurant.isPresent()) {
      LOGGER.error("Restaurant not found for ID: {}", foodItemInDTO.getRestaurantId());
      throw new ResourceNotFoundException(Constants.RESTAURANT_NOT_FOUND);
    }

    LOGGER.debug("Fetching food category with ID: {}", foodItemInDTO.getFoodCategoryId());
    Optional<FoodCategory> existingCategory = foodCategoryRepository.findById(foodItemInDTO.getFoodCategoryId());
    if (!existingCategory.isPresent()) {
      LOGGER.error("Food category not found for ID: {}", foodItemInDTO.getFoodCategoryId());
      throw new ResourceNotFoundException(Constants.FOOD_CATEGORY_NOT_FOUND);
    }

    LOGGER.debug("Checking if food item '{}' already exists in restaurant with ID: {}",
      foodItemInDTO.getFoodItemName(), foodItemInDTO.getRestaurantId());
    Optional<FoodItem> existingFoodItem = foodItemRepository.findByFoodItemNameAndRestaurantId(
      foodItemInDTO.getFoodItemName().toUpperCase(), foodItemInDTO.getRestaurantId());
    if (existingFoodItem.isPresent()) {
      LOGGER.warn("Food item '{}' already exists in restaurant ID: {}",
        foodItemInDTO.getFoodItemName(), foodItemInDTO.getRestaurantId());
      throw new ResourceAlreadyExistException(Constants.FOOD_ITEM_ALREADY_PRESENT);
    }

    LOGGER.debug("Converting FoodItemInDTO to FoodItem entity");
    FoodItem foodItem = FoodItemDtoConverter.inDtoToEntity(foodItemInDTO);
    try {
      if (Objects.nonNull(image) && !image.isEmpty()) {
        LOGGER.debug("Processing image for food item: {}", foodItemInDTO.getFoodItemName());
        String contentType = image.getContentType();
        if (Objects.isNull(contentType) || !(contentType.equals("image/jpeg") || contentType.equals("image/png"))) {
          LOGGER.error("Invalid image type: {}. Only JPG and PNG are allowed.", contentType);
          throw new InvalidRequestException(Constants.INVALID_FILE_TYPE);
        }
        foodItem.setFoodItemImage(image.getBytes());
      }
    } catch (Exception e) {
      LOGGER.error("Error occurred while processing image for food item '{}': {}",
        foodItemInDTO.getFoodItemName(), e.getMessage());
      throw new RuntimeException(Constants.ERROR_PROCESSING_FOOD_ITEM_IMAGE, e);
    }
    LOGGER.debug("Saving food item to the database");
    FoodItem savedFoodItem = foodItemRepository.save(foodItem);
    LOGGER.info("Successfully added food item '{}' for restaurant ID: {}", savedFoodItem.getFoodItemName(),
      savedFoodItem.getRestaurantId());
    FoodItemDtoConverter.entityToOutDTO(savedFoodItem);
    LOGGER.debug("Returning success response for adding food item '{}'", savedFoodItem.getFoodItemName());
    return new CommonResponse(Constants.FOOD_ITEM_ADDED_SUCCESS);
  }

  /**
   * Retrieves a list of food items for a specific category.
   *
   * @param categoryId The ID of the food category whose items are to be retrieved.
   * @return A list of DTOs containing the details of the food items in the specified category.
   * @throws ResourceNotFoundException If no food items are found for the category ID.
   */
  @Override
  public List<FoodItemOutDTO> getFoodItemsByCategory(final Integer categoryId) {
    LOGGER.info("Fetching food items for category ID: {}", categoryId);
    List<FoodItem> foodItems = foodItemRepository.findByCategoryId(categoryId);
    if (foodItems.isEmpty()) {
      LOGGER.error("No food items found for category ID: {}", categoryId);
      throw new ResourceNotFoundException(Constants.NO_FOOD_ITEM_PRESENT);
    }
    List<FoodItemOutDTO> foodItemOutDTOList = new ArrayList<>();
    for (FoodItem foodItem : foodItems) {
      FoodItemOutDTO foodItemOutDTO = FoodItemDtoConverter.entityToOutDTO(foodItem);
      foodItemOutDTOList.add(foodItemOutDTO);
    }
    LOGGER.info("Found {} food items for category ID: {}", foodItemOutDTOList.size(), categoryId);
    return foodItemOutDTOList;
  }

  /**
   * Retrieves a list of food items for a specific restaurant.
   *
   * @param restaurantId The ID of the restaurant whose food items are to be retrieved.
   * @return A list of DTOs containing the details of the food items in the specified restaurant.
   * @throws ResourceNotFoundException If no food items are found for the restaurant ID.
   */
  @Override
  public List<FoodItemOutDTO> getFoodItemsByRestaurant(final Integer restaurantId) {
    LOGGER.info("Fetching food items for restaurant ID: {}", restaurantId);
    List<FoodItem> foodItems = foodItemRepository.findByRestaurantId(restaurantId);
    if (foodItems.isEmpty()) {
      LOGGER.error("No food items found for restaurant ID: {}", restaurantId);
      throw new ResourceNotFoundException(Constants.NO_FOOD_ITEM_PRESENT);
    }
    List<FoodItemOutDTO> foodItemOutDTOList = new ArrayList<>();
    for (FoodItem foodItem : foodItems) {
      FoodItemOutDTO foodItemOutDTO = FoodItemDtoConverter.entityToOutDTO(foodItem);
      foodItemOutDTOList.add(foodItemOutDTO);
    }
    LOGGER.info("Found {} food items for restaurant ID: {}", foodItemOutDTOList.size(), restaurantId);
    return foodItemOutDTOList;
  }

  /**
   * Updates an existing food item by its ID.
   *
   * @param foodItemId          The ID of the food item to be updated.
   * @param foodItemUpdateInDTO The DTO containing the updated details of the food item.
   * @return A response indicating the outcome of the operation.
   * @throws RuntimeException If an error occurs while processing the food item image.
   */
  @Override
  public CommonResponse updateFoodItemByFoodItemId(final Integer foodItemId, final FoodItemUpdateInDTO foodItemUpdateInDTO) {
    LOGGER.info("Attempting to update food item with ID: {}", foodItemId);
    FoodItem existingFoodItem = findFoodItemById(foodItemId);
    try {
      updateFoodItemRequest(foodItemUpdateInDTO, existingFoodItem);
    } catch (IOException e) {
      LOGGER.error("Error processing food item image for ID: {}", foodItemId, e);
      throw new RuntimeException(Constants.ERROR_PROCESSING_FOOD_ITEM_IMAGE, e);
    }
    FoodItem updatedFoodItem = foodItemRepository.save(existingFoodItem);
    LOGGER.info("Successfully updated food item with ID: {}", foodItemId);
    FoodItemDtoConverter.entityToOutDTO(updatedFoodItem);
    return new CommonResponse(Constants.FOOD_ITEM_UPDATED_SUCCESS);
  }

  /**
   * Updates the properties of an existing food item based on the provided update DTO.
   *
   * @param foodItemInDTO    The DTO containing the updated details of the food item.
   * @param existingFoodItem The existing food item entity to be updated.
   * @throws IOException If an error occurs while processing the food item image.
   */
  private void updateFoodItemRequest(final FoodItemUpdateInDTO foodItemInDTO,
                                     final FoodItem existingFoodItem) throws IOException {
    existingFoodItem.setFoodItemName(foodItemInDTO.getFoodItemName().toUpperCase());
    existingFoodItem.setDescription(foodItemInDTO.getDescription());
    existingFoodItem.setPrice(foodItemInDTO.getPrice());

    if (Objects.nonNull(foodItemInDTO.getFoodItemImage()) && !foodItemInDTO.getFoodItemImage().isEmpty()) {
      LOGGER.info("Updating food item image for food item: {}", existingFoodItem.getFoodItemName());
      existingFoodItem.setFoodItemImage(foodItemInDTO.getFoodItemImage().getBytes());
    }
  }

  /**
   * Retrieves the image of a food item by its ID.
   *
   * @param id The ID of the food item whose image is to be retrieved.
   * @return A byte array representing the image of the food item.
   */
  @Override
  public byte[] getFoodItemImage(final Integer id) {
    LOGGER.info("Fetching food item image for ID: {}", id);

    FoodItem foodItem = findFoodItemById(id);
    return foodItem.getFoodItemImage();
  }

  /**
   * Finds a food item by its ID.
   *
   * @param id The ID of the food item to be found.
   * @return The food item entity with the specified ID.
   * @throws ResourceNotFoundException If no food item is found with the specified ID.
   */
  public FoodItem findFoodItemById(final Integer id) {
    LOGGER.info("Looking for food item with ID: {}", id);

    return foodItemRepository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException(Constants.NO_FOOD_ITEM_PRESENT));
  }
}
