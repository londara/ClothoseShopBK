package com.aurastudio.backend.common;

/**
 * Khmer user-facing messages, identical to the Next.js app
 * (docs/API_AND_DATABASE.md §12).
 */
public final class Messages {

	private Messages() {
	}

	// auth
	public static final String LOGIN_REQUIRED_FIELDS = "សូមបញ្ចូលអ៊ីមែល ឬលេខទូរស័ព្ទ និងពាក្យសម្ងាត់";
	public static final String INVALID_CREDENTIALS = "អ៊ីមែល/លេខទូរស័ព្ទ ឬពាក្យសម្ងាត់មិនត្រឹមត្រូវ";
	public static final String ACCOUNT_PENDING = "គណនីរបស់អ្នកកំពុងរង់ចាំការអនុម័តពីម្ចាស់ហាង។";
	public static final String ACCOUNT_DISABLED = "គណនីរបស់អ្នកត្រូវបានផ្អាក។ សូមទាក់ទងម្ចាស់ហាង។";
	public static final String SHOP_SUSPENDED = "ហាងរបស់អ្នកត្រូវបានផ្អាកដោយអ្នកគ្រប់គ្រងវេទិកា។";
	public static final String TOKEN_EXPIRED = "សម័យប្រើប្រាស់បានផុតកំណត់ សូមចូលម្តងទៀត";
	public static final String TOKEN_INVALID = "សូមចូលប្រើប្រាស់ជាមុនសិន";
	public static final String FORBIDDEN = "អ្នកមិនមានសិទ្ធិធ្វើសកម្មភាពនេះទេ";
	public static final String PASSWORD_CHANGED = "បានប្តូរពាក្យសម្ងាត់ដោយជោគជ័យ";
	public static final String CURRENT_PASSWORD_WRONG = "ពាក្យសម្ងាត់បច្ចុប្បន្នមិនត្រឹមត្រូវ";
	public static final String NEW_PASSWORD_TOO_SHORT = "ពាក្យសម្ងាត់ថ្មីត្រូវមានយ៉ាងហោចណាស់ ៨ តួអក្សរ";
	public static final String NEW_PASSWORDS_DIFFER = "ពាក្យសម្ងាត់ថ្មីទាំងពីរមិនដូចគ្នា";

	// accounts
	public static final String NAME_REQUIRED = "សូមបញ្ចូលឈ្មោះរបស់អ្នក";
	public static final String STAFF_NAME_REQUIRED = "សូមបញ្ចូលឈ្មោះបុគ្គលិក";
	public static final String EMAIL_OR_PHONE_REQUIRED = "សូមបញ្ចូលអ៊ីមែល ឬលេខទូរស័ព្ទយ៉ាងហោចណាស់មួយ";
	public static final String PROFILE_EMAIL_OR_PHONE_REQUIRED = "ត្រូវមានអ៊ីមែល ឬលេខទូរស័ព្ទយ៉ាងហោចណាស់មួយ ដើម្បីចូលប្រើ";
	public static final String EMAIL_INVALID = "អ៊ីមែលមិនត្រឹមត្រូវ";
	public static final String PHONE_INVALID = "លេខទូរស័ព្ទមិនត្រឹមត្រូវ (៨ ទៅ ១៥ ខ្ទង់)";
	public static final String EMAIL_TAKEN = "អ៊ីមែលនេះត្រូវបានប្រើរួចហើយ";
	public static final String PHONE_TAKEN = "លេខទូរស័ព្ទនេះត្រូវបានប្រើរួចហើយ";
	public static final String PASSWORD_TOO_SHORT = "ពាក្យសម្ងាត់ត្រូវមានយ៉ាងហោចណាស់ ៨ តួអក្សរ";
	public static final String PASSWORDS_DIFFER = "ពាក្យសម្ងាត់ទាំងពីរមិនដូចគ្នា";
	public static final String PROFILE_SAVED = "បានរក្សាទុកព័ត៌មានផ្ទាល់ខ្លួន";

	// shops
	public static final String SHOP_NAME_REQUIRED = "សូមបញ្ចូលឈ្មោះហាង";
	public static final String SLUG_FORMAT = "តំណហាងអាចមានតែអក្សរឡាតាំងតូច (a-z) លេខ និងសញ្ញា - ប៉ុណ្ណោះ";
	public static final String SLUG_LENGTH = "តំណហាងត្រូវមានពី ៣ ទៅ ៤០ តួអក្សរ";
	public static final String SLUG_RESERVED = "តំណនេះត្រូវបានរក្សាទុក សូមជ្រើសរើសឈ្មោះផ្សេង";
	public static final String SLUG_TAKEN = "តំណហាងនេះមានគេប្រើរួចហើយ";
	public static final String TELEGRAM_INVALID = "ឈ្មោះអ្នកប្រើតេឡេក្រាមមិនត្រឹមត្រូវ (៥–៣២ តួអក្សរ a-z, 0-9, _)";
	public static final String SHOP_TYPE_REQUIRED = "សូមជ្រើសរើសប្រភេទហាង";
	public static final String SHOP_CREATED = "ហាងរបស់អ្នកត្រូវបានបង្កើត ហើយកំពុងរង់ចាំការអនុម័ត";
	public static final String SHOP_NOT_FOUND = "រកមិនឃើញហាង";
	public static final String SETTINGS_SAVED = "បានរក្សាទុកការកំណត់ហាង";
	public static final String BACKGROUND_INVALID = "ពណ៌ផ្ទៃខាងក្រោយត្រូវតែជាកូដពណ៌ (ឧ. #f8f9fa)";
	public static final String HERO_IMAGE_INVALID = "រូបភាពផ្ទៃខាងក្រោយមិនត្រឹមត្រូវ";

	// products
	public static final String PRODUCT_NOT_FOUND = "រកមិនឃើញផលិតផលនេះទេ";
	public static final String PRODUCT_NAME_REQUIRED = "សូមបញ្ចូលឈ្មោះផលិតផល";
	public static final String PRICE_INVALID = "តម្លៃត្រូវតែជាលេខធំជាង ០";
	public static final String STOCK_INVALID = "ចំនួនស្តុកត្រូវតែជាលេខគត់ ០ ឬច្រើនជាងនេះ";
	public static final String DESCRIPTION_REQUIRED = "សូមបញ្ចូលការពិពណ៌នា";
	public static final String CATEGORY_REQUIRED = "សូមជ្រើសរើសប្រភេទយ៉ាងហោចណាស់មួយ";
	public static final String IMAGE_REQUIRED = "សូមបន្ថែមរូបភាពយ៉ាងហោចណាស់មួយ";
	public static final String IMAGE_INVALID_PREFIX = "តំណរូបភាពមិនត្រឹមត្រូវ៖ ";
	public static final String SIZE_REQUIRED = "សូមជ្រើសរើសទំហំយ៉ាងហោចណាស់មួយ";
	public static final String COLOR_NAME_REQUIRED = "សូមដាក់ឈ្មោះឲ្យពណ៌នីមួយៗ";
	public static final String COLOR_HEX_INVALID = "កូដពណ៌មិនត្រឹមត្រូវ";
	public static final String COLOR_NAMES_DUPLICATE = "ឈ្មោះពណ៌មិនអាចដដែលៗបានទេ";
	public static final String COLORS_TOO_MANY = "អាចបន្ថែមពណ៌បានយ៉ាងច្រើន ១២";
	public static final String PRODUCT_CREATED = "បានបន្ថែមផលិតផលថ្មីដោយជោគជ័យ";
	public static final String PRODUCT_UPDATED = "បានកែប្រែផលិតផលដោយជោគជ័យ";

	// categories
	public static final String CATEGORY_NAME_REQUIRED = "សូមបញ្ចូលឈ្មោះប្រភេទ";
	public static final String CATEGORY_TAKEN = "មានប្រភេទឈ្មោះនេះរួចហើយ";
	public static final String CATEGORIES_TOO_MANY = "អាចមានប្រភេទបានយ៉ាងច្រើន ២០";

	// staff
	public static final String ACCOUNT_NOT_FOUND = "រកមិនឃើញគណនី";
	public static final String OWNER_STATUS_LOCKED = "មិនអាចប្តូរស្ថានភាពគណនីម្ចាស់ហាងបានទេ";
	public static final String OWNER_DELETE_LOCKED = "មិនអាចលុបគណនីម្ចាស់ហាងបានទេ";
	public static final String STAFF_APPROVED = "បានអនុម័តគណនី";
	public static final String STAFF_DISABLED = "បានផ្អាកគណនី";
	public static final String STAFF_PENDING = "បានកំណត់គណនីជារង់ចាំ";

	// platform
	public static final String SHOP_APPROVED = "ហាងត្រូវបានអនុម័ត ហើយបង្ហាញជាសាធារណៈ";
	public static final String SHOP_SUSPENDED_DONE = "បានផ្អាកហាង";
	public static final String SHOP_PENDING_DONE = "បានកំណត់ហាងជារង់ចាំ";
	public static final String OWN_SHOP_SUSPEND = "មិនអាចផ្អាកហាងរបស់អ្នកផ្ទាល់បានទេ";
	public static final String OWN_SHOP_DELETE = "មិនអាចលុបហាងរបស់អ្នកផ្ទាល់បានទេ";

	// uploads
	public static final String FILE_TYPE = "អនុញ្ញាតតែឯកសាររូបភាព (JPG, PNG, WEBP, GIF, AVIF) ប៉ុណ្ណោះ";
	public static final String FILE_TOO_LARGE = "រូបភាពត្រូវមានទំហំមិនលើស ៥MB";

	public static final String NOT_FOUND = "រកមិនឃើញទំព័រនេះទេ";
	public static final String INTERNAL = "មានបញ្ហាបច្ចេកទេស សូមព្យាយាមម្តងទៀត";
	public static final String BAD_REQUEST = "ទិន្នន័យដែលបានផ្ញើមិនត្រឹមត្រូវ";

	// input safety (docs/features/20-input-security.md)
	public static final String INVALID_CHARACTERS = "មានតួអក្សរដែលមិនអនុញ្ញាត";
	public static final String PASSWORD_TOO_LONG = "ពាក្យសម្ងាត់វែងពេក (អតិបរមា ៧២ បៃ)";
	public static final String URL_INVALID = "តំណត្រូវតែចាប់ផ្តើមដោយ https:// ហើយមិនវែងពេក";
	public static final String TOO_MANY_ITEMS = "មានធាតុច្រើនពេក";
	public static final String PAYLOAD_TOO_LARGE = "ទិន្នន័យដែលបានផ្ញើធំពេក";
	public static final String RATE_LIMITED = "សំណើច្រើនពេក។ សូមរង់ចាំបន្តិច រួចព្យាយាមម្តងទៀត។";
	public static final String SEARCH_TOO_LONG = "ពាក្យស្វែងរកវែងពេក";

	/** "វែងពេក (អតិបរមា {max} តួអក្សរ)" */
	public static String tooLong(int max) {
		return "វែងពេក (អតិបរមា " + Khmer.digits(max) + " តួអក្សរ)";
	}

	/** "ព្យាយាមខុសច្រើនដងពេក។ សូមរង់ចាំ {minutes} នាទី រួចព្យាយាមម្តងទៀត។" */
	public static String locked(long minutes) {
		return "ព្យាយាមខុសច្រើនដងពេក។ សូមរង់ចាំ " + Khmer.digits(minutes) + " នាទី រួចព្យាយាមម្តងទៀត។";
	}

	public static String staffCreated(String name) {
		return "បានបង្កើតគណនីសម្រាប់ " + name;
	}

	public static String categoryAdded(String label) {
		return "បានបន្ថែមប្រភេទ «" + label + "»";
	}
}
