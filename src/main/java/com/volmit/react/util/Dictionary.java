/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.react.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Dictionary {
    //!\"#$%&[]
    private static final Pattern wordsPattern = Pattern.compile("[A-Z][A-Z][A-Z][A-Z]*");
    private static final Pattern longCodesPattern = Pattern.compile("<[N-Z0-9!\\\\\"#$%\\[\\]& _][A-Z0-9!\\\\\"#$%\\[\\]& _]");
    private static final Pattern shortCodesPattern = Pattern.compile("<[A-M]");

    private static final Comparator<String> byLength = new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
            return b.length() - a.length();
        }
    };


    private static final String[] wordsAndCodes = {
        "A", "THE", "B", "AND", "C", "FOR", "D", "YOU", "E", "NOT", "F", "ARE",
        "G", "ALL", "H", "NEW", "I", "WAS", "J", "CAN", "K", "HAS", "L", "BUT",
        "M", "OUR",
        "NA", "THAT", "NB", "THIS", "NC", "WITH", "ND", "FROM", "NE", "YOUR",
        "NF", "HAVE", "NG", "MORE", "NH", "WILL", "NI", "HOME", "NJ", "ABOUT",
        "NK", "PAGE", "NL", "SEARCH", "NM", "FREE", "NN", "OTHER", "NO", "INFORMATION",
        "NP", "TIME", "NQ", "THEY", "NR", "SITE", "NS", "WHAT", "NT", "WHICH",
        "NU", "THEIR", "NV", "NEWS", "NW", "THERE", "NX", "ONLY", "NY", "WHEN",
        "NZ", "CONTACT", "N0", "HERE", "N1", "BUSINESS", "N2", "ALSO", "N3", "HELP",
        "N4", "VIEW", "N5", "ONLINE", "N6", "FIRST", "N7", "BEEN", "N8", "WOULD",
        "N9", "WERE", "N!", "SERVICES", "N\"", "SOME", "N#", "THESE", "N$", "CLICK",
        "N%", "LIKE", "N&", "SERVICE", "N[", "THAN", "N]", "FIND", "N ", "PRICE",
        "N_", "DATE", "OA", "BACK", "OB", "PEOPLE", "OC", "LIST", "OD", "NAME",
        "OE", "JUST", "OF", "OVER", "OG", "STATE", "OH", "YEAR", "OI", "INTO",
        "OJ", "EMAIL", "OK", "HEALTH", "OL", "WORLD", "OM", "NEXT", "ON", "USED",
        "OO", "WORK", "OP", "LAST", "OQ", "MOST", "OR", "PRODUCTS", "OS", "MUSIC",
        "OT", "DATA", "OU", "MAKE", "OV", "THEM", "OW", "SHOULD", "OX", "PRODUCT",
        "OY", "SYSTEM", "OZ", "POST", "O0", "CITY", "O1", "POLICY", "O2", "NUMBER",
        "O3", "SUCH", "O4", "PLEASE", "O5", "AVAILABLE", "O6", "COPYRIGHT", "O7", "SUPPORT",
        "O8", "MESSAGE", "O9", "AFTER", "O!", "BEST", "O\"", "SOFTWARE", "O#", "THEN",
        "O$", "GOOD", "O%", "VIDEO", "O&", "WELL", "O[", "WHERE", "O]", "INFO",
        "O ", "RIGHTS", "O_", "PUBLIC", "PA", "BOOKS", "PB", "HIGH", "PC", "SCHOOL",
        "PD", "THROUGH", "PE", "EACH", "PF", "LINKS", "PG", "REVIEW", "PH", "YEARS",
        "PI", "ORDER", "PJ", "VERY", "PK", "PRIVACY", "PL", "BOOK", "PM", "ITEMS",
        "PN", "COMPANY", "PO", "READ", "PP", "GROUP", "PQ", "NEED", "PR", "MANY",
        "PS", "USER", "PT", "SAID", "PU", "DOES", "PV", "UNDER", "PW", "GENERAL",
        "PX", "RESEARCH", "PY", "UNIVERSITY", "PZ", "JANUARY", "P0", "MAIL", "P1", "FULL",
        "P2", "REVIEWS", "P3", "PROGRAM", "P4", "LIFE", "P5", "KNOW", "P6", "GAMES",
        "P7", "DAYS", "P8", "MANAGEMENT", "P9", "PART", "P!", "COULD", "P\"", "GREAT",
        "P#", "UNITED", "P$", "HOTEL", "P%", "REAL", "P&", "ITEM", "P[", "INTERNATIONAL",
        "P]", "CENTER", "P ", "EBAY", "P_", "MUST", "QA", "STORE", "QB", "TRAVEL",
        "QC", "COMMENTS", "QD", "MADE", "QE", "DEVELOPMENT", "QF", "REPORT", "QG", "MEMBER",
        "QH", "DETAILS", "QI", "LINE", "QJ", "TERMS", "QK", "BEFORE", "QL", "HOTELS",
        "QM", "SEND", "QN", "RIGHT", "QO", "TYPE", "QP", "BECAUSE", "QQ", "LOCAL",
        "QR", "THOSE", "QS", "USING", "QT", "RESULTS", "QU", "OFFICE", "QV", "EDUCATION",
        "QW", "NATIONAL", "QX", "DESIGN", "QY", "TAKE", "QZ", "POSTED", "Q0", "INTERNET",
        "Q1", "ADDRESS", "Q2", "COMMUNITY", "Q3", "WITHIN", "Q4", "STATES", "Q5", "AREA",
        "Q6", "WANT", "Q7", "PHONE", "Q8", "SHIPPING", "Q9", "RESERVED", "Q!", "SUBJECT",
        "Q\"", "BETWEEN", "Q#", "FORUM", "Q$", "FAMILY", "Q%", "LONG", "Q&", "BASED",
        "Q[", "CODE", "Q]", "SHOW", "Q ", "EVEN", "Q_", "BLACK", "RA", "CHECK",
        "RB", "SPECIAL", "RC", "PRICES", "RD", "WEBSITE", "RE", "INDEX", "RF", "BEING",
        "RG", "WOMEN", "RH", "MUCH", "RI", "SIGN", "RJ", "FILE", "RK", "LINK",
        "RL", "OPEN", "RM", "TODAY", "RN", "TECHNOLOGY", "RO", "SOUTH", "RP", "CASE",
        "RQ", "PROJECT", "RR", "SAME", "RS", "PAGES", "RT", "VERSION", "RU", "SECTION",
        "RV", "FOUND", "RW", "SPORTS", "RX", "HOUSE", "RY", "RELATED", "RZ", "SECURITY",
        "R0", "BOTH", "R1", "COUNTY", "R2", "AMERICAN", "R3", "PHOTO", "R4", "GAME",
        "R5", "MEMBERS", "R6", "POWER", "R7", "WHILE", "R8", "CARE", "R9", "NETWORK",
        "R!", "DOWN", "R\"", "COMPUTER", "R#", "SYSTEMS", "R$", "THREE", "R%", "TOTAL",
        "R&", "PLACE", "R[", "FOLLOWING", "R]", "DOWNLOAD", "R ", "WITHOUT", "R_", "ACCESS",
        "SA", "THINK", "SB", "NORTH", "SC", "RESOURCES", "SD", "CURRENT", "SE", "POSTS",
        "SF", "MEDIA", "SG", "CONTROL", "SH", "WATER", "SI", "HISTORY", "SJ", "PICTURES",
        "SK", "SIZE", "SL", "PERSONAL", "SM", "SINCE", "SN", "INCLUDING", "SO", "GUIDE",
        "SP", "SHOP", "SQ", "DIRECTORY", "SR", "BOARD", "SS", "LOCATION", "ST", "CHANGE",
        "SU", "WHITE", "SV", "TEXT", "SW", "SMALL", "SX", "RATING", "SY", "RATE",
        "SZ", "GOVERNMENT", "S0", "CHILDREN", "S1", "DURING", "S2", "RETURN", "S3", "STUDENTS",
        "S4", "SHOPPING", "S5", "ACCOUNT", "S6", "TIMES", "S7", "SITES", "S8", "LEVEL",
        "S9", "DIGITAL", "S!", "PROFILE", "S\"", "PREVIOUS", "S#", "FORM", "S$", "EVENTS",
        "S%", "LOVE", "S&", "JOHN", "S[", "MAIN", "S]", "CALL", "S ", "HOURS",
        "S_", "IMAGE", "TA", "DEPARTMENT", "TB", "TITLE", "TC", "DESCRIPTION", "TD", "INSURANCE",
        "TE", "ANOTHER", "TF", "SHALL", "TG", "PROPERTY", "TH", "CLASS", "TI", "STILL",
        "TJ", "MONEY", "TK", "QUALITY", "TL", "EVERY", "TM", "LISTING", "TN", "CONTENT",
        "TO", "COUNTRY", "TP", "PRIVATE", "TQ", "LITTLE", "TR", "VISIT", "TS", "SAVE",
        "TT", "TOOLS", "TU", "REPLY", "TV", "CUSTOMER", "TW", "DECEMBER", "TX", "COMPARE",
        "TY", "MOVIES", "TZ", "INCLUDE", "T0", "COLLEGE", "T1", "VALUE", "T2", "ARTICLE",
        "T3", "YORK", "T4", "CARD", "T5", "JOBS", "T6", "PROVIDE", "T7", "FOOD",
        "T8", "SOURCE", "T9", "AUTHOR", "T!", "DIFFERENT", "T\"", "PRESS", "T#", "LEARN",
        "T$", "SALE", "T%", "AROUND", "T&", "PRINT", "T[", "COURSE", "T]", "CANADA",
        "T ", "PROCESS", "T_", "TEEN", "UA", "ROOM", "UB", "STOCK", "UC", "TRAINING",
        "UD", "CREDIT", "UE", "POINT", "UF", "JOIN", "UG", "SCIENCE", "UH", "CATEGORIES",
        "UI", "ADVANCED", "UJ", "WEST", "UK", "SALES", "UL", "LOOK", "UM", "ENGLISH",
        "UN", "LEFT", "UO", "TEAM", "UP", "ESTATE", "UQ", "CONDITIONS", "UR", "SELECT",
        "US", "WINDOWS", "UT", "PHOTOS", "UU", "THREAD", "UV", "WEEK", "UW", "CATEGORY",
        "UX", "NOTE", "UY", "LIVE", "UZ", "LARGE", "U0", "GALLERY", "U1", "TABLE",
        "U2", "REGISTER", "U3", "HOWEVER", "U4", "JUNE", "U5", "OCTOBER", "U6", "NOVEMBER",
        "U7", "MARKET", "U8", "LIBRARY", "U9", "REALLY", "U!", "ACTION", "U\"", "START",
        "U#", "SERIES", "U$", "MODEL", "U%", "FEATURES", "U&", "INDUSTRY", "U[", "PLAN",
        "U]", "HUMAN", "U ", "PROVIDED", "U_", "REQUIRED", "VA", "SECOND", "VB", "ACCESSORIES",
        "VC", "COST", "VD", "MOVIE", "VE", "FORUMS", "VF", "MARCH", "VG", "SEPTEMBER",
        "VH", "BETTER", "VI", "QUESTIONS", "VJ", "JULY", "VK", "YAHOO", "VL", "GOING",
        "VM", "MEDICAL", "VN", "TEST", "VO", "FRIEND", "VP", "COME", "VQ", "SERVER",
        "VR", "STUDY", "VS", "APPLICATION", "VT", "CART", "VU", "STAFF", "VV", "ARTICLES",
        "VW", "FEEDBACK", "VX", "AGAIN", "VY", "PLAY", "VZ", "LOOKING", "V0", "ISSUES",
        "V1", "APRIL", "V2", "NEVER", "V3", "USERS", "V4", "COMPLETE", "V5", "STREET",
        "V6", "TOPIC", "V7", "COMMENT", "V8", "FINANCIAL", "V9", "THINGS", "V!", "WORKING",
        "V\"", "AGAINST", "V#", "STANDARD", "V$", "PERSON", "V%", "BELOW", "V&", "MOBILE",
        "V[", "LESS", "V]", "BLOG", "V ", "PARTY", "V_", "PAYMENT", "WA", "EQUIPMENT",
        "WB", "LOGIN", "WC", "STUDENT", "WD", "PROGRAMS", "WE", "OFFERS", "WF", "LEGAL",
        "WG", "ABOVE", "WH", "RECENT", "WI", "PARK", "WJ", "STORES", "WK", "SIDE",
        "WL", "PROBLEM", "WM", "GIVE", "WN", "MEMORY", "WO", "PERFORMANCE", "WP", "SOCIAL",
        "WQ", "AUGUST", "WR", "QUOTE", "WS", "LANGUAGE", "WT", "STORY", "WU", "SELL",
        "WV", "OPTIONS", "WW", "EXPERIENCE", "WX", "RATES", "WY", "CREATE", "WZ", "BODY",
        "W0", "YOUNG", "W1", "AMERICA", "W2", "IMPORTANT", "W3", "FIELD", "W4", "EAST",
        "W5", "PAPER", "W6", "SINGLE", "W7", "ACTIVITIES", "W8", "CLUB", "W9", "EXAMPLE",
        "W!", "GIRLS", "W\"", "ADDITIONAL", "W#", "PASSWORD", "W$", "LATEST", "W%", "SOMETHING",
        "W&", "ROAD", "W[", "GIFT", "W]", "QUESTION", "W ", "CHANGES", "W_", "NIGHT",
        "XA", "HARD", "XB", "TEXAS", "XC", "FOUR", "XD", "POKER", "XE", "STATUS",
        "XF", "BROWSE", "XG", "ISSUE", "XH", "RANGE", "XI", "BUILDING", "XJ", "SELLER",
        "XK", "COURT", "XL", "FEBRUARY", "XM", "ALWAYS", "XN", "RESULT", "XO", "AUDIO",
        "XP", "LIGHT", "XQ", "WRITE", "XR", "OFFER", "XS", "BLUE", "XT", "GROUPS",
        "XU", "EASY", "XV", "GIVEN", "XW", "FILES", "XX", "EVENT", "XY", "RELEASE",
        "XZ", "ANALYSIS", "X0", "REQUEST", "X1", "CHINA", "X2", "MAKING", "X3", "PICTURE",
        "X4", "NEEDS", "X5", "POSSIBLE", "X6", "MIGHT", "X7", "PROFESSIONAL", "X8", "MONTH",
        "X9", "MAJOR", "X!", "STAR", "X\"", "AREAS", "X#", "FUTURE", "X$", "SPACE",
        "X%", "COMMITTEE", "X&", "HAND", "X[", "CARDS", "X]", "PROBLEMS", "X ", "LONDON",
        "X_", "WASHINGTON", "YA", "MEETING", "YB", "BECOME", "YC", "INTEREST", "YD", "CHILD",
        "YE", "KEEP", "YF", "ENTER", "YG", "CALIFORNIA", "YH", "PORN", "YI", "SHARE",
        "YJ", "SIMILAR", "YK", "GARDEN", "YL", "SCHOOLS", "YM", "MILLION", "YN", "ADDED",
        "YO", "REFERENCE", "YP", "COMPANIES", "YQ", "LISTED", "YR", "BABY", "YS", "LEARNING",
        "YT", "ENERGY", "YU", "DELIVERY", "YV", "POPULAR", "YW", "TERM", "YX", "FILM",
        "YY", "STORIES", "YZ", "COMPUTERS", "Y0", "JOURNAL", "Y1", "REPORTS", "Y2", "WELCOME",
        "Y3", "CENTRAL", "Y4", "IMAGES", "Y5", "PRESIDENT", "Y6", "NOTICE", "Y7", "ORIGINAL",
        "Y8", "HEAD", "Y9", "RADIO", "Y!", "UNTIL", "Y\"", "CELL", "Y#", "COLOR",
        "Y$", "SELF", "Y%", "COUNCIL", "Y&", "AWAY", "Y[", "INCLUDES", "Y]", "TRACK",
        "Y ", "AUSTRALIA", "Y_", "DISCUSSION", "ZA", "ARCHIVE", "ZB", "ONCE", "ZC", "OTHERS",
        "ZD", "ENTERTAINMENT", "ZE", "AGREEMENT", "ZF", "FORMAT", "ZG", "LEAST", "ZH", "SOCIETY",
        "ZI", "MONTHS", "ZJ", "SAFETY", "ZK", "FRIENDS", "ZL", "SURE", "ZM", "TRADE",
        "ZN", "EDITION", "ZO", "CARS", "ZP", "MESSAGES", "ZQ", "MARKETING", "ZR", "TELL",
        "ZS", "FURTHER", "ZT", "UPDATED", "ZU", "ASSOCIATION", "ZV", "ABLE", "ZW", "HAVING",
        "ZX", "PROVIDES", "ZY", "DAVID", "ZZ", "ALREADY", "Z0", "GREEN", "Z1", "STUDIES",
        "Z2", "CLOSE", "Z3", "COMMON", "Z4", "DRIVE", "Z5", "SPECIFIC", "Z6", "SEVERAL",
        "Z7", "GOLD", "Z8", "LIVING", "Z9", "COLLECTION", "Z!", "CALLED", "Z\"", "SHORT",
        "Z#", "ARTS", "Z$", "DISPLAY", "Z%", "LIMITED", "Z&", "POWERED", "Z[", "SOLUTIONS",
        "Z]", "MEANS", "Z ", "DIRECTOR", "Z_", "DAILY", "0A", "BEACH", "0B", "PAST",
        "0C", "NATURAL", "0D", "WHETHER", "0E", "ELECTRONICS", "0F", "FIVE", "0G", "UPON",
        "0H", "PERIOD", "0I", "PLANNING", "0J", "DATABASE", "0K", "SAYS", "0L", "OFFICIAL",
        "0M", "WEATHER", "0N", "LAND", "0O", "AVERAGE", "0P", "DONE", "0Q", "TECHNICAL",
        "0R", "WINDOW", "0S", "FRANCE", "0T", "REGION", "0U", "ISLAND", "0V", "RECORD",
        "0W", "DIRECT", "0X", "MICROSOFT", "0Y", "CONFERENCE", "0Z", "ENVIRONMENT", "00", "RECORDS",
        "01", "DISTRICT", "02", "CALENDAR", "03", "COSTS", "04", "STYLE", "05", "FRONT",
        "06", "STATEMENT", "07", "UPDATE", "08", "PARTS", "09", "EVER", "0!", "DOWNLOADS",
        "0\"", "EARLY", "0#", "MILES", "0$", "SOUND", "0%", "RESOURCE", "0&", "PRESENT",
        "0[", "APPLICATIONS", "0]", "EITHER", "0 ", "DOCUMENT", "0_", "WORD", "1A", "WORKS",
        "1B", "MATERIAL", "1C", "BILL", "1D", "WRITTEN", "1E", "TALK", "1F", "FEDERAL",
        "1G", "HOSTING", "1H", "RULES", "1I", "FINAL", "1J", "ADULT", "1K", "TICKETS",
        "1L", "THING", "1M", "CENTRE", "1N", "REQUIREMENTS", "1O", "CHEAP", "1P", "NUDE",
        "1Q", "KIDS", "1R", "FINANCE", "1S", "TRUE", "1T", "MINUTES", "1U", "ELSE",
        "1V", "MARK", "1W", "THIRD", "1X", "ROCK", "1Y", "GIFTS", "1Z", "EUROPE",
        "10", "READING", "11", "TOPICS", "12", "INDIVIDUAL", "13", "TIPS", "14", "PLUS",
        "15", "AUTO", "16", "COVER", "17", "USUALLY", "18", "EDIT", "19", "TOGETHER",
        "1!", "VIDEOS", "1\"", "PERCENT", "1#", "FAST", "1$", "FUNCTION", "1%", "FACT",
        "1&", "UNIT", "1[", "GETTING", "1]", "GLOBAL", "1 ", "TECH", "1_", "MEET",
        "2A", "ECONOMIC", "2B", "PLAYER", "2C", "PROJECTS", "2D", "LYRICS", "2E", "OFTEN",
        "2F", "SUBSCRIBE", "2G", "SUBMIT", "2H", "GERMANY", "2I", "AMOUNT", "2J", "WATCH",
        "2K", "INCLUDED", "2L", "FEEL", "2M", "THOUGH", "2N", "BANK", "2O", "RISK",
        "2P", "THANKS", "2Q", "EVERYTHING", "2R", "DEALS", "2S", "VARIOUS", "2T", "WORDS",
        "2U", "LINUX", "2V", "PRODUCTION", "2W", "COMMERCIAL", "2X", "JAMES", "2Y", "WEIGHT",
        "2Z", "TOWN", "20", "HEART", "21", "ADVERTISING", "22", "RECEIVED", "23", "CHOOSE",
        "24", "TREATMENT", "25", "NEWSLETTER", "26", "ARCHIVES", "27", "POINTS", "28", "KNOWLEDGE",
        "29", "MAGAZINE", "2!", "ERROR", "2\"", "CAMERA", "2#", "GIRL", "2$", "CURRENTLY",
        "2%", "CONSTRUCTION", "2&", "TOYS", "2[", "REGISTERED", "2]", "CLEAR", "2 ", "GOLF",
        "2_", "RECEIVE", "3A", "DOMAIN", "3B", "METHODS", "3C", "CHAPTER", "3D", "MAKES",
        "3E", "PROTECTION", "3F", "POLICIES", "3G", "LOAN", "3H", "WIDE", "3I", "BEAUTY",
        "3J", "MANAGER", "3K", "INDIA", "3L", "POSITION", "3M", "TAKEN", "3N", "SORT",
        "3O", "LISTINGS", "3P", "MODELS", "3Q", "MICHAEL", "3R", "KNOWN", "3S", "HALF",
        "3T", "CASES", "3U", "STEP", "3V", "ENGINEERING", "3W", "FLORIDA", "3X", "SIMPLE",
        "3Y", "QUICK", "3Z", "NONE", "30", "WIRELESS", "31", "LICENSE", "32", "PAUL",
        "33", "FRIDAY", "34", "LAKE", "35", "WHOLE", "36", "ANNUAL", "37", "PUBLISHED",
        "38", "LATER", "39", "BASIC", "3!", "SONY", "3\"", "SHOWS", "3#", "CORPORATE",
        "3$", "GOOGLE", "3%", "CHURCH", "3&", "METHOD", "3[", "PURCHASE", "3]", "CUSTOMERS",
        "3 ", "ACTIVE", "3_", "RESPONSE", "4A", "PRACTICE", "4B", "HARDWARE", "4C", "FIGURE",
        "4D", "MATERIALS", "4E", "FIRE", "4F", "HOLIDAY", "4G", "CHAT", "4H", "ENOUGH",
        "4I", "DESIGNED", "4J", "ALONG", "4K", "AMONG", "4L", "DEATH", "4M", "WRITING",
        "4N", "SPEED", "4O", "HTML", "4P", "COUNTRIES", "4Q", "LOSS", "4R", "FACE",
        "4S", "BRAND", "4T", "DISCOUNT", "4U", "HIGHER", "4V", "EFFECTS", "4W", "CREATED",
        "4X", "REMEMBER", "4Y", "STANDARDS", "4Z", "YELLOW", "40", "POLITICAL", "41", "INCREASE",
        "42", "ADVERTISE", "43", "KINGDOM", "44", "BASE", "45", "NEAR", "46", "ENVIRONMENTAL",
        "47", "THOUGHT", "48", "STUFF", "49", "FRENCH", "4!", "STORAGE", "4\"", "JAPAN",
        "4#", "DOING", "4$", "LOANS", "4%", "SHOES", "4&", "ENTRY", "4[", "STAY",
        "4]", "NATURE", "4 ", "ORDERS", "4_", "AVAILABILITY", "5A", "AFRICA", "5B", "SUMMARY",
        "5C", "TURN", "5D", "MEAN", "5E", "GROWTH", "5F", "NOTES", "5G", "AGENCY",
        "5H", "KING", "5I", "MONDAY", "5J", "EUROPEAN", "5K", "ACTIVITY", "5L", "COPY",
        "5M", "ALTHOUGH", "5N", "DRUG", "5O", "PICS", "5P", "WESTERN", "5Q", "INCOME",
        "5R", "FORCE", "5S", "CASH", "5T", "EMPLOYMENT", "5U", "OVERALL", "5V", "RIVER",
        "5W", "COMMISSION", "5X", "PACKAGE", "5Y", "CONTENTS", "5Z", "SEEN", "50", "PLAYERS",
        "51", "ENGINE", "52", "PORT", "53", "ALBUM", "54", "REGIONAL", "55", "STOP",
        "56", "SUPPLIES", "57", "STARTED", "58", "ADMINISTRATION", "59", "INSTITUTE", "5!", "VIEWS",
        "5\"", "PLANS", "5#", "DOUBLE", "5$", "BUILD", "5%", "SCREEN", "5&", "EXCHANGE",
        "5[", "TYPES", "5]", "SOON", "5 ", "SPONSORED", "5_", "LINES", "6A", "ELECTRONIC",
        "6B", "CONTINUE", "6C", "ACROSS", "6D", "BENEFITS", "6E", "NEEDED", "6F", "SEASON",
        "6G", "APPLY", "6H", "SOMEONE", "6I", "HELD", "6J", "ANYTHING", "6K", "PRINTER",
        "6L", "CONDITION", "6M", "EFFECTIVE", "6N", "BELIEVE", "6O", "ORGANIZATION", "6P", "EFFECT",
        "6Q", "ASKED", "6R", "MIND", "6S", "SUNDAY", "6T", "SELECTION", "6U", "CASINO",
        "6V", "LOST", "6W", "TOUR", "6X", "MENU", "6Y", "VOLUME", "6Z", "CROSS",
        "60", "ANYONE", "61", "MORTGAGE", "62", "HOPE", "63", "SILVER", "64", "CORPORATION",
        "65", "WISH", "66", "INSIDE", "67", "SOLUTION", "68", "MATURE", "69", "ROLE",
        "6!", "RATHER", "6\"", "WEEKS", "6#", "ADDITION", "6$", "CAME", "6%", "SUPPLY",
        "6&", "NOTHING", "6[", "CERTAIN", "6]", "EXECUTIVE", "6 ", "RUNNING", "6_", "LOWER",
        "7A", "NECESSARY", "7B", "UNION", "7C", "JEWELRY", "7D", "ACCORDING", "7E", "CLOTHING",
        "7F", "PARTICULAR", "7G", "FINE", "7H", "NAMES", "7I", "ROBERT", "7J", "HOMEPAGE",
        "7K", "HOUR", "7L", "SKILLS", "7M", "BUSH", "7N", "ISLANDS", "7O", "ADVICE",
        "7P", "CAREER", "7Q", "MILITARY", "7R", "RENTAL", "7S", "DECISION", "7T", "LEAVE",
        "7U", "BRITISH", "7V", "TEENS", "7W", "HUGE", "7X", "WOMAN", "7Y", "FACILITIES",
        "7Z", "KIND", "70", "SELLERS", "71", "MIDDLE", "72", "MOVE", "73", "CABLE",
        "74", "OPPORTUNITIES", "75", "TAKING", "76", "VALUES", "77", "DIVISION", "78", "COMING",
        "79", "TUESDAY", "7!", "OBJECT", "7\"", "LESBIAN", "7#", "APPROPRIATE", "7$", "MACHINE",
        "7%", "LOGO", "7&", "LENGTH", "7[", "ACTUALLY", "7]", "NICE", "7 ", "SCORE",
        "7_", "STATISTICS", "8A", "CLIENT", "8B", "RETURNS", "8C", "CAPITAL", "8D", "FOLLOW",
        "8E", "SAMPLE", "8F", "INVESTMENT", "8G", "SENT", "8H", "SHOWN", "8I", "SATURDAY",
        "8J", "CHRISTMAS", "8K", "ENGLAND", "8L", "CULTURE", "8M", "BAND", "8N", "FLASH",
        "8O", "LEAD", "8P", "GEORGE", "8Q", "CHOICE", "8R", "WENT", "8S", "STARTING",
        "8T", "REGISTRATION", "8U", "THURSDAY", "8V", "COURSES", "8W", "CONSUMER", "8X", "AIRPORT",
        "8Y", "FOREIGN", "8Z", "ARTIST", "80", "OUTSIDE", "81", "FURNITURE", "82", "LEVELS",
        "83", "CHANNEL", "84", "LETTER", "85", "MODE", "86", "PHONES", "87", "IDEAS",
        "88", "WEDNESDAY", "89", "STRUCTURE", "8!", "FUND", "8\"", "SUMMER", "8#", "ALLOW",
        "8$", "DEGREE", "8%", "CONTRACT", "8&", "BUTTON", "8[", "RELEASES", "8]", "HOMES",
        "8 ", "SUPER", "8_", "MALE", "9A", "MATTER", "9B", "CUSTOM", "9C", "VIRGINIA",
        "9D", "ALMOST", "9E", "TOOK", "9F", "LOCATED", "9G", "MULTIPLE", "9H", "ASIAN",
        "9I", "DISTRIBUTION", "9J", "EDITOR", "9K", "INDUSTRIAL", "9L", "CAUSE", "9M", "POTENTIAL",
        "9N", "SONG", "9O", "CNET", "9P", "FOCUS", "9Q", "LATE", "9R", "FALL",
        "9S", "FEATURED", "9T", "IDEA", "9U", "ROOMS", "9V", "FEMALE", "9W", "RESPONSIBLE",
        "9X", "COMMUNICATIONS", "9Y", "ASSOCIATED", "9Z", "THOMAS", "90", "PRIMARY", "91", "CANCER",
        "92", "NUMBERS", "93", "REASON", "94", "TOOL", "95", "BROWSER", "96", "SPRING",
        "97", "FOUNDATION", "98", "ANSWER", "99", "VOICE", "9!", "FRIENDLY", "9\"", "SCHEDULE",
        "9#", "DOCUMENTS", "9$", "COMMUNICATION", "9%", "PURPOSE", "9&", "FEATURE", "9[", "COMES",
        "9]", "POLICE", "9 ", "EVERYONE", "9_", "INDEPENDENT", "!A", "APPROACH", "!B", "CAMERAS",
        "!C", "BROWN", "!D", "PHYSICAL", "!E", "OPERATING", "!F", "HILL", "!G", "MAPS",
        "!H", "MEDICINE", "!I", "DEAL", "!J", "HOLD", "!K", "RATINGS", "!L", "CHICAGO",
        "!M", "FORMS", "!N", "GLASS", "!O", "HAPPY", "!P", "SMITH", "!Q", "WANTED",
        "!R", "DEVELOPED", "!S", "THANK", "!T", "SAFE", "!U", "UNIQUE", "!V", "SURVEY",
        "!W", "PRIOR", "!X", "TELEPHONE", "!Y", "SPORT", "!Z", "READY", "!0", "FEED",
        "!1", "ANIMAL", "!2", "SOURCES", "!3", "MEXICO", "!4", "POPULATION", "!5", "REGULAR",
        "!6", "SECURE", "!7", "NAVIGATION", "!8", "OPERATIONS", "!9", "THEREFORE", "!!", "SIMPLY",
        "!\"", "EVIDENCE", "!#", "STATION", "!$", "CHRISTIAN", "!%", "ROUND", "!&", "PAYPAL",
        "![", "FAVORITE", "!]", "UNDERSTAND", "! ", "OPTION", "!_", "MASTER", "\"A", "VALLEY",
        "\"B", "RECENTLY", "\"C", "PROBABLY", "\"D", "RENTALS", "\"E", "BUILT", "\"F", "PUBLICATIONS",
        "\"G", "BLOOD", "\"H", "WORLDWIDE", "\"I", "IMPROVE", "\"J", "CONNECTION", "\"K", "PUBLISHER",
        "\"L", "HALL", "\"M", "LARGER", "\"N", "ANTI", "\"O", "NETWORKS", "\"P", "EARTH",
        "\"Q", "PARENTS", "\"R", "NOKIA", "\"S", "IMPACT", "\"T", "TRANSFER", "\"U", "INTRODUCTION",
        "\"V", "KITCHEN", "\"W", "STRONG", "\"X", "CAROLINA", "\"Y", "WEDDING", "\"Z", "PROPERTIES",
        "\"0", "HOSPITAL", "\"1", "GROUND", "\"2", "OVERVIEW", "\"3", "SHIP", "\"4", "ACCOMMODATION",
        "\"5", "OWNERS", "\"6", "DISEASE", "\"7", "EXCELLENT", "\"8", "PAID", "\"9", "ITALY",
        "\"!", "PERFECT", "\"\"", "HAIR", "\"#", "OPPORTUNITY", "\"$", "CLASSIC", "\"%", "BASIS",
        "\"&", "COMMAND", "\"[", "CITIES", "\"]", "WILLIAM", "\" ", "EXPRESS", "\"_", "ANAL",
        "#A", "AWARD", "#B", "DISTANCE", "#C", "TREE", "#D", "PETER", "#E", "ASSESSMENT",
        "#F", "ENSURE", "#G", "THUS", "#H", "WALL", "#I", "INVOLVED", "#J", "EXTRA",
        "#K", "ESPECIALLY", "#L", "INTERFACE", "#M", "PUSSY", "#N", "PARTNERS", "#O", "BUDGET",
        "#P", "RATED", "#Q", "GUIDES", "#R", "SUCCESS", "#S", "MAXIMUM", "#T", "OPERATION",
        "#U", "EXISTING", "#V", "QUITE", "#W", "SELECTED", "#X", "AMAZON", "#Y", "PATIENTS",
        "#Z", "RESTAURANTS", "#0", "BEAUTIFUL", "#1", "WARNING", "#2", "WINE", "#3", "LOCATIONS",
        "#4", "HORSE", "#5", "VOTE", "#6", "FORWARD", "#7", "FLOWERS", "#8", "STARS",
        "#9", "SIGNIFICANT", "#!", "LISTS", "#\"", "TECHNOLOGIES", "##", "OWNER", "#$", "RETAIL",
        "#%", "ANIMALS", "#&", "USEFUL", "#[", "DIRECTLY", "#]", "MANUFACTURER", "# ", "WAYS",
        "#_", "PROVIDING", "$A", "RULE", "$B", "HOUSING", "$C", "TAKES", "$D", "BRING",
        "$E", "CATALOG", "$F", "SEARCHES", "$G", "TRYING", "$H", "MOTHER", "$I", "AUTHORITY",
        "$J", "CONSIDERED", "$K", "TOLD", "$L", "TRAFFIC", "$M", "PROGRAMME", "$N", "JOINED",
        "$O", "INPUT", "$P", "STRATEGY", "$Q", "FEET", "$R", "AGENT", "$S", "VALID",
        "$T", "MODERN", "$U", "SENIOR", "$V", "IRELAND", "$W", "SEXY", "$X", "TEACHING",
        "$Y", "DOOR", "$Z", "GRAND", "$0", "TESTING", "$1", "TRIAL", "$2", "CHARGE",
        "$3", "UNITS", "$4", "INSTEAD", "$5", "CANADIAN", "$6", "COOL", "$7", "NORMAL",
        "$8", "WROTE", "$9", "ENTERPRISE", "$!", "SHIPS", "$\"", "ENTIRE", "$#", "EDUCATIONAL",
        "$$", "LEADING", "$%", "METAL", "$&", "POSITIVE", "$[", "FITNESS", "$]", "CHINESE",
        "$ ", "OPINION", "$_", "ASIA", "%A", "FOOTBALL", "%B", "ABSTRACT", "%C", "USES",
        "%D", "OUTPUT", "%E", "FUNDS", "%F", "GREATER", "%G", "LIKELY", "%H", "DEVELOP",
        "%I", "EMPLOYEES", "%J", "ARTISTS", "%K", "ALTERNATIVE", "%L", "PROCESSING", "%M", "RESPONSIBILITY",
        "%N", "RESOLUTION", "%O", "JAVA", "%P", "GUEST", "%Q", "SEEMS", "%R", "PUBLICATION",
        "%S", "PASS", "%T", "RELATIONS", "%U", "TRUST", "%V", "CONTAINS", "%W", "SESSION",
        "%X", "MULTI", "%Y", "PHOTOGRAPHY", "%Z", "REPUBLIC", "%0", "FEES", "%1", "COMPONENTS",
        "%2", "VACATION", "%3", "CENTURY", "%4", "ACADEMIC", "%5", "ASSISTANCE", "%6", "COMPLETED",
        "%7", "SKIN", "%8", "GRAPHICS", "%9", "INDIAN", "%!", "PREV", "%\"", "MARY",
        "%#", "EXPECTED", "%$", "RING", "%%", "GRADE", "%&", "DATING", "%[", "PACIFIC",
        "%]", "MOUNTAIN", "% ", "ORGANIZATIONS", "%_", "FILTER", "&A", "MAILING", "&B", "VEHICLE",
        "&C", "LONGER", "&D", "CONSIDER", "&E", "NORTHERN", "&F", "BEHIND", "&G", "PANEL",
        "&H", "FLOOR", "&I", "GERMAN", "&J", "BUYING", "&K", "MATCH", "&L", "PROPOSED",
        "&M", "DEFAULT", "&N", "REQUIRE", "&O", "IRAQ", "&P", "BOYS", "&Q", "OUTDOOR",
        "&R", "DEEP", "&S", "MORNING", "&T", "OTHERWISE", "&U", "ALLOWS", "&V", "REST",
        "&W", "PROTEIN", "&X", "PLANT", "&Y", "REPORTED", "&Z", "TRANSPORTATION", "&0", "POOL",
        "&1", "MINI", "&2", "POLITICS", "&3", "PARTNER", "&4", "DISCLAIMER", "&5", "AUTHORS",
        "&6", "BOARDS", "&7", "FACULTY", "&8", "PARTIES", "&9", "FISH", "&!", "MEMBERSHIP",
        "&\"", "MISSION", "&#", "STRING", "&$", "SENSE", "&%", "MODIFIED", "&&", "PACK",
        "&[", "RELEASED", "&]", "STAGE", "& ", "INTERNAL", "&_", "GOODS", "[A", "RECOMMENDED",
        "[B", "BORN", "[C", "UNLESS", "[D", "RICHARD", "[E", "DETAILED", "[F", "JAPANESE",
        "[G", "RACE", "[H", "APPROVED", "[I", "BACKGROUND", "[J", "TARGET", "[K", "EXCEPT",
        "[L", "CHARACTER", "[M", "MAINTENANCE", "[N", "ABILITY", "[O", "MAYBE", "[P", "FUNCTIONS",
        "[Q", "MOVING", "[R", "BRANDS", "[S", "PLACES", "[T", "PRETTY", "[U", "TRADEMARKS",
        "[V", "PHENTERMINE", "[W", "SPAIN", "[X", "SOUTHERN", "[Y", "YOURSELF", "[Z", "WINTER",
        "[0", "RAPE", "[1", "BATTERY", "[2", "YOUTH", "[3", "PRESSURE", "[4", "SUBMITTED",
        "[5", "BOSTON", "[6", "INCEST", "[7", "DEBT", "[8", "KEYWORDS", "[9", "MEDIUM",
        "[!", "TELEVISION", "[\"", "INTERESTED", "[#", "CORE", "[$", "BREAK", "[%", "PURPOSES",
        "[&", "THROUGHOUT", "[[", "SETS", "[]", "DANCE", "[ ", "WOOD", "[_", "ITSELF",
        "]A", "DEFINED", "]B", "PAPERS", "]C", "PLAYING", "]D", "AWARDS", "]E", "STUDIO",
        "]F", "READER", "]G", "VIRTUAL", "]H", "DEVICE", "]I", "ESTABLISHED", "]J", "ANSWERS",
        "]K", "RENT", "]L", "REMOTE", "]M", "DARK", "]N", "PROGRAMMING", "]O", "EXTERNAL",
        "]P", "APPLE", "]Q", "REGARDING", "]R", "INSTRUCTIONS", "]S", "OFFERED", "]T", "THEORY",
        "]U", "ENJOY", "]V", "REMOVE", "]W", "SURFACE", "]X", "MINIMUM", "]Y", "VISUAL",
        "]Z", "HOST", "]0", "VARIETY", "]1", "TEACHERS", "]2", "ISBN", "]3", "MARTIN",
        "]4", "MANUAL", "]5", "BLOCK", "]6", "SUBJECTS", "]7", "AGENTS", "]8", "INCREASED",
        "]9", "REPAIR", "]!", "FAIR", "]\"", "CIVIL", "]#", "STEEL", "]$", "UNDERSTANDING",
        "]%", "SONGS", "]&", "FIXED", "][", "WRONG", "]]", "BEGINNING", "] ", "HANDS",
        "]_", "ASSOCIATES", " A", "FINALLY", " B", "UPDATES", " C", "DESKTOP", " D", "CLASSES",
        " E", "PARIS", " F", "OHIO", " G", "GETS", " H", "SECTOR", " I", "CAPACITY",
        " J", "REQUIRES", " K", "JERSEY", " L", "FULLY", " M", "FATHER", " N", "ELECTRIC",
        " O", "INSTRUMENTS", " P", "QUOTES", " Q", "OFFICER", " R", "DRIVER", " S", "BUSINESSES",
        " T", "DEAD", " U", "RESPECT", " V", "UNKNOWN", " W", "SPECIFIED", " X", "RESTAURANT",
        " Y", "MIKE", " Z", "TRIP", " 0", "WORTH", " 1", "PROCEDURES", " 2", "POOR",
        " 3", "TEACHER", " 4", "EYES", " 5", "RELATIONSHIP", " 6", "WORKERS", " 7", "FARM",
        " 8", "HTTP://", " 9", "GEORGIA", " !", "PEACE", " \"", "TRADITIONAL", " #", "CAMPUS",
        " $", "SHOWING", " %", "CREATIVE", " &", "COAST", " [", "BENEFIT", " ]", "PROGRESS",
        "  ", "FUNDING", " _", "DEVICES", "_A", "LORD", "_B", "GRANT", "_C", "AGREE",
        "_D", "FICTION", "_E", "HEAR", "_F", "SOMETIMES", "_G", "WATCHES", "_H", "CAREERS",
        "_I", "BEYOND", "_J", "GOES", "_K", "FAMILIES", "_L", "MUSEUM", "_M", "THEMSELVES",
        "_N", "TRANSPORT", "_O", "INTERESTING", "_P", "BLOGS", "_Q", "WIFE", "_R", "EVALUATION",
        "_S", "ACCEPTED", "_T", "FORMER", "_U", "IMPLEMENTATION", "_V", "HITS", "_W", "ZONE",
        "_X", "COMPLEX", "_Y", "GALLERIES", "_Z", "REFERENCES", "_0", "PRESENTED", "_1", "JACK",
        "_2", "FLAT", "_3", "FLOW", "_4", "AGENCIES", "_5", "LITERATURE", "_6", "RESPECTIVE",
        "_7", "PARENT", "_8", "SPANISH", "_9", "MICHIGAN", "_!", "COLUMBIA", "_\"", "SETTING",
        "_#", "SCALE", "_$", "STAND", "_%", "ECONOMY", "_&", "HIGHEST", "_[", "HELPFUL",
        "_]", "MONTHLY", "_ ", "CRITICAL", "__", "FRAME"
    };
    private static final Map<String, String> wordsToCodes = new HashMap<String, String>();
    private static final Map<String, String> codesToWords = new HashMap<String, String>();

    static {
        for(int i = 0; i < wordsAndCodes.length; i = i + 2) {
            String code = wordsAndCodes[i];
            String word = wordsAndCodes[i + 1];
            wordsToCodes.put(word, code);
            codesToWords.put(code, word);
        }
    }

    public static String encode(String word) {
        if(wordsToCodes.containsKey(word)) {
            return "<" + wordsToCodes.get(word);
        }
        return word;
    }

    public static String decode(String code1) {
        if(!code1.startsWith("<")) {
            return code1;
        }
        String code = code1.substring(1);
        if(codesToWords.containsKey(code)) {
            return codesToWords.get(code);
        }
        return code1;
    }

    public static String lengthen(String result2) {
        List<String> shortCodesInString = wordsForPattern(shortCodesPattern, result2);
        for(String w : shortCodesInString) {
            String theWord = Dictionary.decode(w);
            result2 = result2.replace(w, theWord);
        }
        List<String> longCodesInString = wordsForPattern(longCodesPattern, result2);
        for(String w : longCodesInString) {
            String theWord = Dictionary.decode(w);
            result2 = result2.replace(w, theWord);
        }
        return result2;
    }

    public static String shorten(String str) {
        List<String> words = wordsForPattern(wordsPattern, str);
        String result = str;
        for(String w : words) {
            String code = Dictionary.encode(w);
            result = result.replace(w, code);
        }
        return result;
    }

    public static List<String> wordsForPattern(Pattern p, String str) {
        Matcher m = p.matcher(str);
        List<String> words = new ArrayList<String>();
        while(m.find()) {
            words.add(m.group(0));
        }

        Collections.sort(words, byLength);
        return words;
    }
}