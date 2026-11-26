package com.example.ghostgrid.downloader

enum class RegionGroup(val label: String) {
    INDIA("India"),
    WORLD("World")
}

data class SeedPlace(
    val name: String,
    val category: String,
    val subcategory: String,
    val address: String,
    val lat: Double,
    val lon: Double
)

data class BuiltInRegionPack(
    val id: String,
    val name: String,
    val subtitle: String,
    val group: RegionGroup,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val centerLat: Double,
    val centerLon: Double,
    val seedPlaces: List<SeedPlace>
)

/**
 * Curated India & World regional offline packs with verified essential places
 * (hospitals, pharmacies, transit hubs, fuel stations, and food hubs).
 * When installed while online, GhostGrid automatically enriches each pack with
 * additional live OpenStreetMap / ArcGIS places inside the region's bounding box
 * before writing an offline SQLite `places.db` inside app-private storage.
 */
object BuiltInRegionCatalog {

    val allPacks: List<BuiltInRegionPack> = listOf(
        // ==================== INDIA ====================
        BuiltInRegionPack(
            id = "india_gujarat",
            name = "Gujarat",
            subtitle = "Ahmedabad, Surat, Vadodara, Rajkot",
            group = RegionGroup.INDIA,
            minLon = 68.10, minLat = 20.10, maxLon = 74.50, maxLat = 24.75,
            centerLat = 23.0225, centerLon = 72.5714,
            seedPlaces = listOf(
                SeedPlace("Civil Hospital Ahmedabad", "hospital", "Hospital", "Asarwa, Ahmedabad, Gujarat", 23.0525, 72.6033),
                SeedPlace("Apollo Hospitals Gandhinagar", "hospital", "Hospital", "Plot 1A, Bhat, Ahmedabad, Gujarat", 23.1102, 72.6279),
                SeedPlace("Sterling Hospital Ahmedabad", "hospital", "Hospital", "Memnagar, Ahmedabad, Gujarat", 23.0462, 72.5312),
                SeedPlace("Zydus Hospital Ahmedabad", "hospital", "Hospital", "SG Highway, Thaltej, Ahmedabad", 23.0585, 72.5171),
                SeedPlace("New Civil Hospital Surat", "hospital", "Hospital", "Majura Gate, Surat, Gujarat", 21.1819, 72.8206),
                SeedPlace("SSG Hospital Vadodara", "hospital", "Hospital", "Jail Road, Vadodara, Gujarat", 22.3027, 73.1924),
                SeedPlace("Apollo Pharmacy Navrangpura", "pharmacy", "Pharmacy", "CG Road, Ahmedabad, Gujarat", 23.0332, 72.5612),
                SeedPlace("MedPlus Pharmacy Satellite", "pharmacy", "Pharmacy", "Satellite Road, Ahmedabad", 23.0274, 72.5262),
                SeedPlace("Ahmedabad Junction (Kalupur)", "transit", "Train Station", "Kalupur, Ahmedabad, Gujarat", 23.0268, 72.6011),
                SeedPlace("Sardar Vallabhbhai Patel Intl Airport", "transit", "Airport", "Hansol, Ahmedabad, Gujarat", 23.0734, 72.6266),
                SeedPlace("Surat Railway Station", "transit", "Train Station", "Station Road, Surat, Gujarat", 21.2049, 72.8407),
                SeedPlace("Vadodara Junction", "transit", "Train Station", "Alkapuri, Vadodara, Gujarat", 22.3107, 73.1812),
                SeedPlace("IndianOil SG Highway Fuel", "fuel", "Fuel Station", "SG Highway, Ahmedabad", 23.0419, 72.5078),
                SeedPlace("HPCL Ashram Road Fuel", "fuel", "Fuel Station", "Ashram Road, Ahmedabad", 23.0378, 72.5698),
                SeedPlace("Manek Chowk Food Hub", "food", "Food Court", "Old City, Ahmedabad, Gujarat", 23.0235, 72.5892),
                SeedPlace("Agashiye", "food", "Restaurant", "Lal Darwaja, Ahmedabad, Gujarat", 23.0272, 72.5815),
                SeedPlace("Swati Snacks Law Garden", "food", "Restaurant", "Ellisbridge, Ahmedabad, Gujarat", 23.0258, 72.5594)
            )
        ),
        BuiltInRegionPack(
            id = "india_delhi_ncr",
            name = "Delhi NCR",
            subtitle = "New Delhi, Gurugram, Noida",
            group = RegionGroup.INDIA,
            minLon = 76.80, minLat = 28.20, maxLon = 77.65, maxLat = 28.90,
            centerLat = 28.6139, centerLon = 77.2090,
            seedPlaces = listOf(
                SeedPlace("AIIMS New Delhi", "hospital", "Hospital", "Ansari Nagar, New Delhi", 28.5672, 77.2100),
                SeedPlace("Safdarjung Hospital", "hospital", "Hospital", "Ring Road, New Delhi", 28.5679, 77.2063),
                SeedPlace("Sir Ganga Ram Hospital", "hospital", "Hospital", "Old Rajinder Nagar, New Delhi", 28.6388, 77.1897),
                SeedPlace("Medanta The Medicity", "hospital", "Hospital", "Sector 38, Gurugram, Haryana", 28.4395, 77.0407),
                SeedPlace("Indraprastha Apollo Hospital", "hospital", "Hospital", "Sarita Vihar, New Delhi", 28.5407, 77.2834),
                SeedPlace("Apollo Pharmacy Connaught Place", "pharmacy", "Pharmacy", "Connaught Place, New Delhi", 28.6315, 77.2167),
                SeedPlace("New Delhi Railway Station", "transit", "Train Station", "Paharganj, New Delhi", 28.6429, 77.2191),
                SeedPlace("Indira Gandhi Intl Airport (DEL)", "transit", "Airport", "Palam, New Delhi", 28.5562, 77.1000),
                SeedPlace("Rajiv Chowk Metro Station", "transit", "Metro Station", "Connaught Place, New Delhi", 28.6328, 77.2197),
                SeedPlace("IndianOil Janpath Fuel", "fuel", "Fuel Station", "Janpath, New Delhi", 28.6225, 77.2185),
                SeedPlace("Karim's Jama Masjid", "food", "Restaurant", "Gali Kababian, Old Delhi", 28.6487, 77.2336),
                SeedPlace("Saravana Bhavan Janpath", "food", "Restaurant", "Connaught Place, New Delhi", 28.6292, 77.2194)
            )
        ),
        BuiltInRegionPack(
            id = "india_maharashtra",
            name = "Maharashtra",
            subtitle = "Mumbai, Pune, Nagpur",
            group = RegionGroup.INDIA,
            minLon = 72.60, minLat = 15.60, maxLon = 80.90, maxLat = 22.10,
            centerLat = 19.0760, centerLon = 72.8777,
            seedPlaces = listOf(
                SeedPlace("KEM Hospital Mumbai", "hospital", "Hospital", "Parel, Mumbai, Maharashtra", 19.0022, 72.8415),
                SeedPlace("Lilavati Hospital Bandra", "hospital", "Hospital", "Bandra West, Mumbai", 19.0510, 72.8286),
                SeedPlace("Kokilaben Dhirubhai Ambani Hospital", "hospital", "Hospital", "Andheri West, Mumbai", 19.1313, 72.8251),
                SeedPlace("Tata Memorial Hospital", "hospital", "Hospital", "Parel, Mumbai, Maharashtra", 19.0049, 72.8432),
                SeedPlace("Ruby Hall Clinic Pune", "hospital", "Hospital", "Sassoon Road, Pune", 18.5335, 73.8774),
                SeedPlace("Wellness Forever Bandra", "pharmacy", "Pharmacy", "Linking Road, Mumbai", 19.0604, 72.8362),
                SeedPlace("Chhatrapati Shivaji Maharaj Terminus", "transit", "Train Station", "Fort, Mumbai", 18.9398, 72.8355),
                SeedPlace("Chhatrapati Shivaji Intl Airport (BOM)", "transit", "Airport", "Santacruz, Mumbai", 19.0896, 72.8656),
                SeedPlace("Pune Junction", "transit", "Train Station", "Agarkar Nagar, Pune", 18.5289, 73.8744),
                SeedPlace("BPCL Worli Sea Face Fuel", "fuel", "Fuel Station", "Worli, Mumbai", 19.0085, 72.8156),
                SeedPlace("Britannia & Co. Ballard Estate", "food", "Restaurant", "Fort, Mumbai", 18.9355, 72.8398),
                SeedPlace("Leopold Cafe Colaba", "food", "Restaurant", "Colaba Causeway, Mumbai", 18.9220, 72.8317)
            )
        ),
        BuiltInRegionPack(
            id = "india_karnataka",
            name = "Karnataka",
            subtitle = "Bengaluru, Mysuru, Mangaluru",
            group = RegionGroup.INDIA,
            minLon = 74.05, minLat = 11.55, maxLon = 78.60, maxLat = 18.45,
            centerLat = 12.9716, centerLon = 77.5946,
            seedPlaces = listOf(
                SeedPlace("NIMHANS Bengaluru", "hospital", "Hospital", "Hosur Road, Bengaluru", 12.9403, 77.5968),
                SeedPlace("Manipal Hospital Old Airport Rd", "hospital", "Hospital", "HAL Old Airport Rd, Bengaluru", 12.9587, 77.6489),
                SeedPlace("St. John's Medical College Hospital", "hospital", "Hospital", "Koramangala, Bengaluru", 12.9298, 77.6203),
                SeedPlace("Apollo Hospital Bannerghatta", "hospital", "Hospital", "Bannerghatta Road, Bengaluru", 12.8960, 77.5986),
                SeedPlace("MedPlus Indiranagar", "pharmacy", "Pharmacy", "100 Feet Road, Bengaluru", 12.9719, 77.6412),
                SeedPlace("KSR Bengaluru City Junction", "transit", "Train Station", "Majestic, Bengaluru", 12.9781, 77.5695),
                SeedPlace("Kempegowda Intl Airport (BLR)", "transit", "Airport", "Devanahalli, Bengaluru", 13.1986, 77.7066),
                SeedPlace("Shell Fuel Station MG Road", "fuel", "Fuel Station", "Trinity Circle, Bengaluru", 12.9735, 77.6194),
                SeedPlace("MTR Lalbagh", "food", "Restaurant", "Lalbagh Road, Bengaluru", 12.9552, 77.5855),
                SeedPlace("Vidyarthi Bhavan", "food", "Restaurant", "Basavanagudi, Bengaluru", 12.9450, 77.5714)
            )
        ),
        BuiltInRegionPack(
            id = "india_tamil_nadu",
            name = "Tamil Nadu",
            subtitle = "Chennai, Coimbatore, Madurai",
            group = RegionGroup.INDIA,
            minLon = 76.20, minLat = 8.05, maxLon = 80.35, maxLat = 13.55,
            centerLat = 13.0827, centerLon = 80.2707,
            seedPlaces = listOf(
                SeedPlace("Apollo Hospitals Greams Road", "hospital", "Hospital", "Thousand Lights, Chennai", 13.0631, 80.2518),
                SeedPlace("Rajiv Gandhi Govt General Hospital", "hospital", "Hospital", "Park Town, Chennai", 13.0815, 80.2772),
                SeedPlace("MIOT International Chennai", "hospital", "Hospital", "Manapakkam, Chennai", 13.0213, 80.1856),
                SeedPlace("Apollo Pharmacy T. Nagar", "pharmacy", "Pharmacy", "Usman Road, Chennai", 13.0418, 80.2341),
                SeedPlace("MGR Chennai Central", "transit", "Train Station", "Park Town, Chennai", 13.0827, 80.2755),
                SeedPlace("Chennai International Airport (MAA)", "transit", "Airport", "Meenambakkam, Chennai", 12.9941, 80.1709),
                SeedPlace("IndianOil Anna Salai Fuel", "fuel", "Fuel Station", "Mount Road, Chennai", 13.0604, 80.2640),
                SeedPlace("Murugan Idli Shop T. Nagar", "food", "Restaurant", "T. Nagar, Chennai", 13.0432, 80.2335),
                SeedPlace("Ratna Cafe Triplicane", "food", "Restaurant", "Triplicane, Chennai", 13.0588, 80.2757)
            )
        ),
        BuiltInRegionPack(
            id = "india_telangana",
            name = "Telangana",
            subtitle = "Hyderabad, Secunderabad, Warangal",
            group = RegionGroup.INDIA,
            minLon = 77.20, minLat = 15.80, maxLon = 81.35, maxLat = 19.95,
            centerLat = 17.3850, centerLon = 78.4867,
            seedPlaces = listOf(
                SeedPlace("NIMS Hospital Punjagutta", "hospital", "Hospital", "Punjagutta, Hyderabad", 17.4222, 78.4519),
                SeedPlace("Apollo Health City Jubilee Hills", "hospital", "Hospital", "Jubilee Hills, Hyderabad", 17.4150, 78.4126),
                SeedPlace("Yashoda Hospitals Somajiguda", "hospital", "Hospital", "Somajiguda, Hyderabad", 17.4258, 78.4572),
                SeedPlace("MedPlus Banjara Hills", "pharmacy", "Pharmacy", "Road No. 12, Hyderabad", 17.4126, 78.4398),
                SeedPlace("Secunderabad Junction", "transit", "Train Station", "Secunderabad, Hyderabad", 17.4344, 78.5013),
                SeedPlace("Rajiv Gandhi Intl Airport (HYD)", "transit", "Airport", "Shamshabad, Hyderabad", 17.2403, 78.4294),
                SeedPlace("HPCL HITEC City Fuel", "fuel", "Fuel Station", "Madhapur, Hyderabad", 17.4483, 78.3915),
                SeedPlace("Paradise Biryani Secunderabad", "food", "Restaurant", "MG Road, Secunderabad", 17.4414, 78.4877),
                SeedPlace("Shah Ghouse Tolichowki", "food", "Restaurant", "Tolichowki, Hyderabad", 17.3996, 78.4162)
            )
        ),
        BuiltInRegionPack(
            id = "india_west_bengal",
            name = "West Bengal",
            subtitle = "Kolkata, Howrah, Siliguri",
            group = RegionGroup.INDIA,
            minLon = 85.80, minLat = 21.50, maxLon = 89.90, maxLat = 27.25,
            centerLat = 22.5726, centerLon = 88.3639,
            seedPlaces = listOf(
                SeedPlace("SSKM Hospital Kolkata", "hospital", "Hospital", "AJC Bose Road, Kolkata", 22.5396, 88.3426),
                SeedPlace("Apollo Multispeciality Kolkata", "hospital", "Hospital", "EM Bypass, Kolkata", 22.5746, 88.4018),
                SeedPlace("Medical College Kolkata", "hospital", "Hospital", "College Street, Kolkata", 22.5736, 88.3612),
                SeedPlace("Frank Ross Pharmacy Park Street", "pharmacy", "Pharmacy", "Park Street, Kolkata", 22.5535, 88.3524),
                SeedPlace("Howrah Junction", "transit", "Train Station", "Howrah, West Bengal", 22.5839, 88.3426),
                SeedPlace("Netaji Subhas Chandra Bose Airport (CCU)", "transit", "Airport", "Dum Dum, Kolkata", 22.6547, 88.4467),
                SeedPlace("IndianOil Park Circus Fuel", "fuel", "Fuel Station", "Park Circus, Kolkata", 22.5431, 88.3652),
                SeedPlace("Peter Cat Park Street", "food", "Restaurant", "Park Street, Kolkata", 22.5528, 88.3526),
                SeedPlace("Flurys Park Street", "food", "Cafe", "Park Street, Kolkata", 22.5553, 88.3521)
            )
        ),
        BuiltInRegionPack(
            id = "india_rajasthan",
            name = "Rajasthan",
            subtitle = "Jaipur, Udaipur, Jodhpur",
            group = RegionGroup.INDIA,
            minLon = 69.50, minLat = 23.05, maxLon = 78.25, maxLat = 30.20,
            centerLat = 26.9124, centerLon = 75.7873,
            seedPlaces = listOf(
                SeedPlace("SMS Hospital Jaipur", "hospital", "Hospital", "JLN Marg, Jaipur", 26.9061, 75.8165),
                SeedPlace("Fortis Escorts Hospital Jaipur", "hospital", "Hospital", "Malviya Nagar, Jaipur", 26.8473, 75.8037),
                SeedPlace("Apollo Pharmacy MI Road", "pharmacy", "Pharmacy", "MI Road, Jaipur", 26.9163, 75.8091),
                SeedPlace("Jaipur Junction", "transit", "Train Station", "Gopalbari, Jaipur", 26.9196, 75.7879),
                SeedPlace("Jaipur International Airport (JAI)", "transit", "Airport", "Sanganer, Jaipur", 26.8242, 75.8122),
                SeedPlace("BPCL Tonk Road Fuel", "fuel", "Fuel Station", "Tonk Road, Jaipur", 26.8752, 75.8024),
                SeedPlace("LMB Johari Bazar", "food", "Restaurant", "Johari Bazar, Jaipur", 26.9192, 75.8266),
                SeedPlace("Rawat Mishtan Bhandar", "food", "Restaurant", "Sindhi Camp, Jaipur", 26.9219, 75.7985)
            )
        ),
        BuiltInRegionPack(
            id = "india_kerala",
            name = "Kerala",
            subtitle = "Kochi, Thiruvananthapuram, Kozhikode",
            group = RegionGroup.INDIA,
            minLon = 74.85, minLat = 8.25, maxLon = 77.45, maxLat = 12.80,
            centerLat = 9.9312, centerLon = 76.2673,
            seedPlaces = listOf(
                SeedPlace("Amrita Hospital Kochi", "hospital", "Hospital", "Ponekkara, Kochi, Kerala", 10.0329, 76.2925),
                SeedPlace("Aster Medcity Kochi", "hospital", "Hospital", "Cheranallur, Kochi, Kerala", 10.0445, 76.2782),
                SeedPlace("KIMS Health Trivandrum", "hospital", "Hospital", "Anayara, Thiruvananthapuram", 8.5143, 76.9082),
                SeedPlace("Neethi Medicals MG Road", "pharmacy", "Pharmacy", "MG Road, Ernakulam, Kochi", 9.9746, 76.2832),
                SeedPlace("Ernakulam Junction South", "transit", "Train Station", "Ernakulam, Kochi", 9.9693, 76.2908),
                SeedPlace("Cochin International Airport (COK)", "transit", "Airport", "Nedumbassery, Kochi", 10.1520, 76.4019),
                SeedPlace("IndianOil Edappally Fuel", "fuel", "Fuel Station", "NH 66, Edappally, Kochi", 10.0261, 76.3083),
                SeedPlace("Paragon Restaurant Kochi", "food", "Restaurant", "Lulu Mall, Edappally, Kochi", 10.0271, 76.3080)
            )
        ),

        // ==================== WORLD ====================
        BuiltInRegionPack(
            id = "world_usa_nyc",
            name = "New York Metro",
            subtitle = "Manhattan, Brooklyn, Queens, Newark",
            group = RegionGroup.WORLD,
            minLon = -74.35, minLat = 40.48, maxLon = -73.68, maxLat = 40.95,
            centerLat = 40.7128, centerLon = -74.0060,
            seedPlaces = listOf(
                SeedPlace("NYU Langone Health", "hospital", "Hospital", "550 1st Ave, New York, NY", 40.7421, -73.9740),
                SeedPlace("Bellevue Hospital Center", "hospital", "Hospital", "462 1st Ave, New York, NY", 40.7392, -73.9754),
                SeedPlace("Mount Sinai Hospital", "hospital", "Hospital", "1468 Madison Ave, New York, NY", 40.7900, -73.9525),
                SeedPlace("NewYork-Presbyterian Weill Cornell", "hospital", "Hospital", "525 E 68th St, New York, NY", 40.7644, -73.9546),
                SeedPlace("CVS Pharmacy Midtown", "pharmacy", "Pharmacy", "150 E 42nd St, New York, NY", 40.7510, -73.9756),
                SeedPlace("Grand Central Terminal", "transit", "Train Station", "89 E 42nd St, New York, NY", 40.7527, -73.9772),
                SeedPlace("Penn Station New York", "transit", "Train Station", "8th Ave & 31st St, New York", 40.7506, -73.9935),
                SeedPlace("JFK International Airport", "transit", "Airport", "Queens, NY 11430", 40.6413, -73.7781),
                SeedPlace("Mobil Fuel West Side", "fuel", "Fuel Station", "10th Ave & 14th St, New York", 40.7422, -74.0080),
                SeedPlace("Katz's Delicatessen", "food", "Restaurant", "205 E Houston St, New York", 40.7222, -73.9874),
                SeedPlace("Chelsea Market", "food", "Food Hall", "75 9th Ave, New York, NY", 40.7424, -74.0061)
            )
        ),
        BuiltInRegionPack(
            id = "world_usa_sf",
            name = "San Francisco Bay",
            subtitle = "San Francisco, Oakland, San Jose",
            group = RegionGroup.WORLD,
            minLon = -122.55, minLat = 37.20, maxLon = -121.75, maxLat = 37.95,
            centerLat = 37.7749, centerLon = -122.4194,
            seedPlaces = listOf(
                SeedPlace("UCSF Medical Center Parnassus", "hospital", "Hospital", "505 Parnassus Ave, San Francisco", 37.7631, -122.4586),
                SeedPlace("Zuckerberg San Francisco General", "hospital", "Hospital", "1001 Potrero Ave, San Francisco", 37.7555, -122.4045),
                SeedPlace("Stanford Health Care", "hospital", "Hospital", "500 Pasteur Dr, Palo Alto, CA", 37.4346, -122.1749),
                SeedPlace("Walgreens Market Street", "pharmacy", "Pharmacy", "825 Market St, San Francisco", 37.7849, -122.4063),
                SeedPlace("San Francisco Intl Airport (SFO)", "transit", "Airport", "San Francisco, CA 94128", 37.6213, -122.3790),
                SeedPlace("Salesforce Transit Center", "transit", "Transit Hub", "425 Mission St, San Francisco", 37.7897, -122.3963),
                SeedPlace("Chevron SoMa Fuel", "fuel", "Fuel Station", "9th St & Harrison St, SF", 37.7731, -122.4094),
                SeedPlace("Ferry Building Marketplace", "food", "Food Hall", "1 Ferry Building, San Francisco", 37.7955, -122.3937),
                SeedPlace("Tartine Bakery", "food", "Bakery", "600 Guerrero St, San Francisco", 37.7615, -122.4241)
            )
        ),
        BuiltInRegionPack(
            id = "world_uk_london",
            name = "London",
            subtitle = "Greater London, Westminster, Heathrow",
            group = RegionGroup.WORLD,
            minLon = -0.55, minLat = 51.28, maxLon = 0.30, maxLat = 51.70,
            centerLat = 51.5072, centerLon = -0.1276,
            seedPlaces = listOf(
                SeedPlace("St Thomas' Hospital", "hospital", "Hospital", "Westminster Bridge Rd, London", 51.4989, -0.1180),
                SeedPlace("University College Hospital", "hospital", "Hospital", "235 Euston Rd, London", 51.5246, -0.1362),
                SeedPlace("St Mary's Hospital Paddington", "hospital", "Hospital", "Praed St, London", 51.5174, -0.1740),
                SeedPlace("Boots Pharmacy Piccadilly", "pharmacy", "Pharmacy", "44 Regent St, London", 51.5101, -0.1353),
                SeedPlace("King's Cross St Pancras", "transit", "Train Station", "Euston Rd, London", 51.5319, -0.1263),
                SeedPlace("London Heathrow Airport (LHR)", "transit", "Airport", "Longford, Hounslow, UK", 51.4700, -0.4543),
                SeedPlace("BP Fuel Park Lane", "fuel", "Fuel Station", "Marylebone Rd, London", 51.5226, -0.1558),
                SeedPlace("Borough Market", "food", "Food Market", "8 Southwark St, London", 51.5055, -0.0910),
                SeedPlace("Dishoom Covent Garden", "food", "Restaurant", "12 Upper St Martin's Ln, London", 51.5124, -0.1270)
            )
        ),
        BuiltInRegionPack(
            id = "world_japan_tokyo",
            name = "Tokyo",
            subtitle = "Shibuya, Shinjuku, Chiyoda, Haneda",
            group = RegionGroup.WORLD,
            minLon = 139.50, minLat = 35.50, maxLon = 140.00, maxLat = 35.85,
            centerLat = 35.6762, centerLon = 139.6503,
            seedPlaces = listOf(
                SeedPlace("St. Luke's International Hospital", "hospital", "Hospital", "9-1 Akashicho, Chuo-ku, Tokyo", 35.6673, 139.7777),
                SeedPlace("University of Tokyo Hospital", "hospital", "Hospital", "7-3-1 Hongo, Bunkyo-ku, Tokyo", 35.7123, 139.7662),
                SeedPlace("Matsumoto Kiyoshi Shibuya", "pharmacy", "Pharmacy", "Udagawacho, Shibuya, Tokyo", 35.6602, 139.7001),
                SeedPlace("Tokyo Station", "transit", "Train Station", "Marunouchi, Chiyoda-ku, Tokyo", 35.6812, 139.7671),
                SeedPlace("Shinjuku Station", "transit", "Train Station", "Shinjuku, Tokyo", 35.6896, 139.7006),
                SeedPlace("Haneda Airport (HND)", "transit", "Airport", "Ota-ku, Tokyo", 35.5494, 139.7798),
                SeedPlace("ENEOS Roppongi Service Station", "fuel", "Fuel Station", "Minato-ku, Tokyo", 35.6641, 139.7314),
                SeedPlace("Tsukiji Outer Market", "food", "Food Market", "Chuo-ku, Tokyo", 35.6655, 139.7707),
                SeedPlace("Ichiran Shibuya", "food", "Restaurant", "Jinnan, Shibuya, Tokyo", 35.6611, 139.7011)
            )
        ),
        BuiltInRegionPack(
            id = "world_uae_dubai",
            name = "Dubai & Abu Dhabi",
            subtitle = "Dubai, Sharjah, Abu Dhabi",
            group = RegionGroup.WORLD,
            minLon = 54.25, minLat = 24.30, maxLon = 55.65, maxLat = 25.45,
            centerLat = 25.2048, centerLon = 55.2708,
            seedPlaces = listOf(
                SeedPlace("Rashid Hospital Dubai", "hospital", "Hospital", "Oud Metha, Dubai, UAE", 25.2420, 55.3177),
                SeedPlace("American Hospital Dubai", "hospital", "Hospital", "Oud Metha, Dubai, UAE", 25.2367, 55.3125),
                SeedPlace("Cleveland Clinic Abu Dhabi", "hospital", "Hospital", "Al Maryah Island, Abu Dhabi", 24.4995, 54.3887),
                SeedPlace("Aster Pharmacy Downtown", "pharmacy", "Pharmacy", "Sheikh Mohammed bin Rashid Blvd, Dubai", 25.1952, 55.2744),
                SeedPlace("Dubai International Airport (DXB)", "transit", "Airport", "Garhoud, Dubai, UAE", 25.2532, 55.3657),
                SeedPlace("Burj Khalifa / Dubai Mall Metro", "transit", "Metro Station", "Sheikh Zayed Rd, Dubai", 25.2013, 55.2697),
                SeedPlace("ENOC Sheikh Zayed Rd Fuel", "fuel", "Fuel Station", "Al Quoz, Dubai, UAE", 25.1618, 55.2341),
                SeedPlace("Al Mallah Satwa", "food", "Restaurant", "Al Dhiyafah Rd, Dubai", 25.2286, 55.2768)
            )
        ),
        BuiltInRegionPack(
            id = "world_singapore",
            name = "Singapore",
            subtitle = "Central, Marina Bay, Changi",
            group = RegionGroup.WORLD,
            minLon = 103.60, minLat = 1.22, maxLon = 104.05, maxLat = 1.48,
            centerLat = 1.3521, centerLon = 103.8198,
            seedPlaces = listOf(
                SeedPlace("Singapore General Hospital", "hospital", "Hospital", "Outram Road, Singapore", 1.2793, 103.8350),
                SeedPlace("Mount Elizabeth Hospital", "hospital", "Hospital", "3 Mount Elizabeth, Singapore", 1.3052, 103.8354),
                SeedPlace("Guardian Pharmacy Orchard", "pharmacy", "Pharmacy", "391 Orchard Rd, Singapore", 1.3025, 103.8347),
                SeedPlace("Singapore Changi Airport (SIN)", "transit", "Airport", "Airport Blvd, Singapore", 1.3644, 103.9915),
                SeedPlace("Raffles Place MRT", "transit", "Metro Station", "5 Raffles Pl, Singapore", 1.2839, 103.8515),
                SeedPlace("Shell Bukit Timah Fuel", "fuel", "Fuel Station", "Bukit Timah Rd, Singapore", 1.3189, 103.8166),
                SeedPlace("Maxwell Food Centre", "food", "Food Court", "1 Kadayanallur St, Singapore", 1.2803, 103.8448),
                SeedPlace("Lau Pa Sat", "food", "Food Court", "18 Raffles Quay, Singapore", 1.2807, 103.8505)
            )
        ),
        BuiltInRegionPack(
            id = "world_france_paris",
            name = "Paris",
            subtitle = "Île-de-France, Orly, CDG",
            group = RegionGroup.WORLD,
            minLon = 2.15, minLat = 48.72, maxLon = 2.60, maxLat = 49.04,
            centerLat = 48.8566, centerLon = 2.3522,
            seedPlaces = listOf(
                SeedPlace("Hôpital Pitié-Salpêtrière", "hospital", "Hospital", "47-83 Bd de l'Hôpital, Paris", 48.8384, 2.3649),
                SeedPlace("Hôpital Hôtel-Dieu", "hospital", "Hospital", "1 Parvis Notre-Dame, Paris", 48.8548, 2.3489),
                SeedPlace("Pharmacie des Champs-Élysées", "pharmacy", "Pharmacy", "84 Av. des Champs-Élysées, Paris", 48.8712, 2.3037),
                SeedPlace("Gare du Nord", "transit", "Train Station", "18 Rue de Dunkerque, Paris", 48.8809, 2.3553),
                SeedPlace("Paris Charles de Gaulle Airport (CDG)", "transit", "Airport", "Roissy-en-France", 49.0097, 2.5479),
                SeedPlace("TotalEnergies Porte Maillot", "fuel", "Fuel Station", "Boulevard Périphérique, Paris", 48.8778, 2.2819),
                SeedPlace("Bouillon Chartier Grands Boulevards", "food", "Restaurant", "7 Rue du Faubourg Montmartre, Paris", 48.8719, 2.3431)
            )
        ),
        BuiltInRegionPack(
            id = "world_germany_berlin",
            name = "Berlin",
            subtitle = "Mitte, Kreuzberg, Charlottenburg",
            group = RegionGroup.WORLD,
            minLon = 13.08, minLat = 52.34, maxLon = 13.76, maxLat = 52.68,
            centerLat = 52.5200, centerLon = 13.4050,
            seedPlaces = listOf(
                SeedPlace("Charité Campus Mitte", "hospital", "Hospital", "Charitéplatz 1, Berlin", 52.5256, 13.3777),
                SeedPlace("Vivantes Klinikum am Urban", "hospital", "Hospital", "Dieffenbachstraße 1, Berlin", 52.4951, 13.4098),
                SeedPlace("Apotheke am Alexanderplatz", "pharmacy", "Pharmacy", "Alexanderplatz, Berlin", 52.5219, 13.4132),
                SeedPlace("Berlin Hauptbahnhof", "transit", "Train Station", "Europaplatz 1, Berlin", 52.5251, 13.3694),
                SeedPlace("Berlin Brandenburg Airport (BER)", "transit", "Airport", "Melli-Beese-Ring, Schönefeld", 52.3667, 13.5033),
                SeedPlace("Aral Fuel Mitte", "fuel", "Fuel Station", "Holzmarktstraße, Berlin", 52.5137, 13.4243),
                SeedPlace("Markthalle Neun", "food", "Food Hall", "Eisenbahnstraße 42, Berlin", 52.5021, 13.4317)
            )
        ),
        BuiltInRegionPack(
            id = "world_australia_sydney",
            name = "Sydney",
            subtitle = "CBD, Parramatta, Bondi",
            group = RegionGroup.WORLD,
            minLon = 150.70, minLat = -34.05, maxLon = 151.35, maxLat = -33.65,
            centerLat = -33.8688, centerLon = 151.2093,
            seedPlaces = listOf(
                SeedPlace("Royal Prince Alfred Hospital", "hospital", "Hospital", "50 Missenden Rd, Camperdown NSW", -33.8894, 151.1830),
                SeedPlace("St Vincent's Hospital Sydney", "hospital", "Hospital", "390 Victoria St, Darlinghurst", -33.8803, 151.2206),
                SeedPlace("Chemist Warehouse George St", "pharmacy", "Pharmacy", "306 George St, Sydney NSW", -33.8669, 151.2072),
                SeedPlace("Central Station Sydney", "transit", "Train Station", "Eddy Ave, Haymarket NSW", -33.8832, 151.2067),
                SeedPlace("Sydney Airport (SYD)", "transit", "Airport", "Mascot NSW 2020", -33.9399, 151.1753),
                SeedPlace("Ampol Woolloomooloo Fuel", "fuel", "Fuel Station", "William St, Sydney NSW", -33.8742, 151.2194),
                SeedPlace("Sydney Fish Market", "food", "Food Market", "Corner Pyrmont Bridge Rd, Sydney", -33.8731, 151.1927)
            )
        ),
        BuiltInRegionPack(
            id = "world_canada_toronto",
            name = "Toronto",
            subtitle = "Downtown, Mississauga, North York",
            group = RegionGroup.WORLD,
            minLon = -79.75, minLat = 43.55, maxLon = -79.10, maxLat = 43.90,
            centerLat = 43.6532, centerLon = -79.3832,
            seedPlaces = listOf(
                SeedPlace("Toronto General Hospital", "hospital", "Hospital", "200 Elizabeth St, Toronto, ON", 43.6596, -79.3881),
                SeedPlace("St. Michael's Hospital", "hospital", "Hospital", "36 Queen St E, Toronto, ON", 43.6535, -79.3777),
                SeedPlace("Shoppers Drug Mart Yonge St", "pharmacy", "Pharmacy", "465 Yonge St, Toronto, ON", 43.6615, -79.3837),
                SeedPlace("Union Station Toronto", "transit", "Train Station", "65 Front St W, Toronto, ON", 43.6453, -79.3806),
                SeedPlace("Toronto Pearson Intl Airport (YYZ)", "transit", "Airport", "Mississauga, ON", 43.6777, -79.6248),
                SeedPlace("Petro-Canada Spadina Fuel", "fuel", "Fuel Station", "Spadina Ave, Toronto, ON", 43.6442, -79.3949),
                SeedPlace("St. Lawrence Market", "food", "Food Market", "93 Front St E, Toronto, ON", 43.6487, -79.3715)
            )
        )
    )

    fun byGroup(group: RegionGroup): List<BuiltInRegionPack> = allPacks.filter { it.group == group }
}
