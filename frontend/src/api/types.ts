export type UserType = 'MARKETER' | 'CUSTOMER' | 'ADMIN';
export type PostingCategory = 'MARKETER_POSTING' | 'CUSTOMER_POSTING';
export type PostingStatus = 'DRAFT' | 'OPEN' | 'CLOSED';
export type OfferStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
export type MessageType = 'USER' | 'SYSTEM';
export type SystemEvent =
  | 'OFFER_MADE'
  | 'OFFER_UPDATED'
  | 'OFFER_ACCEPTED'
  | 'OFFER_REJECTED'
  | 'OFFER_WITHDRAWN'
  | 'POSTING_CLOSED'
  | 'POSTING_REOPENED';
export type ReportStatus = 'OPEN' | 'RESOLVED';
export type ReportResolution = 'DISMISSED' | 'MESSAGE_DELETED';

export interface User {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  userType: UserType;
  createdAt: string;
  updatedAt: string;
}

export interface UserSummary {
  id: string | null;
  fullName: string;
  userType: UserType | null;
}

export interface AuthResponse {
  tokenType: 'Bearer';
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string | null;
  refreshTokenExpiresAt: string;
  user: User;
}

export interface Image {
  id: string;
  contentType: string;
  sizeBytes: number;
  url: string;
  urlExpiresAt: string;
  createdAt: string;
}

export interface Posting {
  id: string;
  category: PostingCategory;
  status: PostingStatus;
  title: string;
  text: string;
  payFrom: number | null;
  payTo: number | null;
  payCurrency: string | null;
  creator: UserSummary;
  images: Image[];
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface Offer {
  id: string;
  posting: {
    id: string;
    title: string;
    category: PostingCategory;
    status: PostingStatus;
    payCurrency: string | null;
    creator: UserSummary;
  };
  offerer: UserSummary;
  text: string;
  status: OfferStatus;
  priceFrom: number;
  priceTo: number;
  currency: string;
  dealId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Currency {
  code: string;
  name: string;
  symbol: string;
  fractionDigits: number;
  unitsPerEur: number | null;
  rateDate: string | null;
}

export interface Message {
  id: string;
  conversationId: string;
  type: MessageType;
  systemEvent: SystemEvent | null;
  sender: UserSummary | null;
  text: string | null;
  offerId: string | null;
  images: Image[];
  deleted: boolean;
  createdAt: string;
  editedAt: string | null;
}

export interface Conversation {
  id: string;
  posting: { id: string; title: string; category: PostingCategory; status: PostingStatus };
  postingCreator: UserSummary;
  counterpart: UserSummary;
  otherParticipant: UserSummary;
  lastMessage: Message | null;
  unreadCount: number;
  otherParticipantLastReadAt: string | null;
  lastMessageAt: string;
  createdAt: string;
}

export interface MessagePage {
  messages: Message[];
  nextBefore: string | null;
}

export interface UnreadCount {
  unreadMessages: number;
  conversationsWithUnread: number;
}

export interface BlockedUser {
  user: UserSummary;
  blockedAt: string;
}

export interface Report {
  id: string;
  conversationId: string;
  message: Message | null;
  reporter: UserSummary;
  reason: string;
  status: ReportStatus;
  resolution: ReportResolution | null;
  resolvedBy: UserSummary | null;
  createdAt: string;
  resolvedAt: string | null;
}

export type ConversationEventType =
  'MESSAGE_CREATED' | 'MESSAGE_UPDATED' | 'MESSAGE_DELETED' | 'CONVERSATION_READ' | 'TYPING';

export interface ConversationEvent {
  type: ConversationEventType;
  conversationId: string;
  message: Message | null;
  actorId: string | null;
  at: string;
}

export type DealStatus = 'IN_PROGRESS' | 'DELIVERED' | 'COMPLETED' | 'CANCELLED';
export type DealRole = 'MARKETER' | 'CUSTOMER';
export type DeliveryStatus = 'PENDING' | 'APPROVED' | 'CHANGES_REQUESTED';
export type SocialProvider = 'TWITCH' | 'YOUTUBE';

export interface Delivery {
  id: string;
  links: string[];
  note: string | null;
  status: DeliveryStatus;
  feedback: string | null;
  submittedAt: string;
  reviewedAt: string | null;
}

export interface Review {
  id: string;
  dealId: string;
  postingTitle: string;
  reviewer: UserSummary;
  rating: number;
  text: string | null;
  createdAt: string;
}

export interface ReviewState {
  canReview: boolean;
  reviewDeadline: string | null;
  mine: Review | null;
  theirsSubmitted: boolean;
  theirs: Review | null;
  revealAt: string | null;
}

export interface Deal {
  id: string;
  offerId: string | null;
  postingId: string | null;
  postingTitle: string;
  marketer: UserSummary;
  customer: UserSummary;
  myRole: DealRole | null;
  amount: number;
  currency: string;
  status: DealStatus;
  createdAt: string;
  deliveredAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  autoCompleteAt: string | null;
  cancelledBy: UserSummary | null;
  cancelReason: string | null;
  conversationId: string | null;
  deliveries: Delivery[];
  reviews: ReviewState | null;
}

export interface SocialAccount {
  id: string;
  provider: SocialProvider;
  handle: string;
  displayName: string;
  profileUrl: string;
  avatarUrl: string | null;
  followers: number;
  totalViews: number | null;
  contentCount: number | null;
  needsReconnect: boolean;
  connectedAt: string;
  lastSyncedAt: string;
}

export interface SocialProviderInfo {
  provider: SocialProvider;
  name: string;
  available: boolean;
}

export interface Profile {
  id: string;
  fullName: string;
  userType: UserType;
  memberSince: string;
  ratingAverage: number;
  ratingCount: number;
  completedDeals: number;
  verifiedReach: number;
  accounts: SocialAccount[];
  recentReviews: Review[];
}
