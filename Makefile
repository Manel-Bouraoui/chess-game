.PHONY: help setup install dev test build clean deploy

# Colors for output
BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[0;33m
NC := \033[0m

help:
	@echo "$(BLUE)Chess Application - Makefile Commands$(NC)"
	@echo ""
	@echo "Setup & Installation:"
	@echo "  $(GREEN)make setup$(NC)         - Full project setup"
	@echo "  $(GREEN)make install$(NC)       - Install all dependencies"
	@echo ""
	@echo "Development:"
	@echo "  $(GREEN)make dev$(NC)           - Start both backend and frontend"
	@echo "  $(GREEN)make backend$(NC)       - Start backend only"
	@echo "  $(GREEN)make frontend$(NC)      - Start frontend only"
	@echo ""
	@echo "Building & Testing:"
	@echo "  $(GREEN)make build$(NC)         - Build both backend and frontend"
	@echo "  $(GREEN)make test$(NC)          - Run all tests"
	@echo "  $(GREEN)make lint$(NC)          - Run linters"
	@echo ""
	@echo "Docker:"
	@echo "  $(GREEN)make docker-up$(NC)     - Start Docker Compose"
	@echo "  $(GREEN)make docker-down$(NC)   - Stop Docker Compose"
	@echo "  $(GREEN)make docker-logs$(NC)   - View Docker logs"
	@echo ""
	@echo "Cleanup:"
	@echo "  $(GREEN)make clean$(NC)         - Clean all build artifacts"
	@echo ""

setup:
	@echo "$(BLUE)Setting up Chess Application...$(NC)"
	@chmod +x QUICK_START.sh
	@./QUICK_START.sh

install:
	@echo "$(BLUE)Installing dependencies...$(NC)"
	@echo "$(YELLOW)Backend:$(NC)"
	@cd backend && ./mvnw clean install
	@echo "$(YELLOW)Frontend:$(NC)"
	@cd frontend && pnpm install

dev:
	@echo "$(BLUE)Starting development servers...$(NC)"
	@echo "$(YELLOW)Backend running on http://localhost:8080$(NC)"
	@echo "$(YELLOW)Frontend running on http://localhost:5173$(NC)"
	@(cd backend && ./mvnw spring-boot:run) & \
	(cd frontend && pnpm dev) & \
	wait

backend:
	@echo "$(BLUE)Starting backend server...$(NC)"
	@cd backend && ./mvnw spring-boot:run

frontend:
	@echo "$(BLUE)Starting frontend server...$(NC)"
	@cd frontend && pnpm dev

build:
	@echo "$(BLUE)Building project...$(NC)"
	@echo "$(YELLOW)Backend:$(NC)"
	@cd backend && ./mvnw clean package
	@echo "$(YELLOW)Frontend:$(NC)"
	@cd frontend && pnpm build

test:
	@echo "$(BLUE)Running tests...$(NC)"
	@echo "$(YELLOW)Backend:$(NC)"
	@cd backend && ./mvnw test
	@echo "$(YELLOW)Frontend:$(NC)"
	@cd frontend && pnpm test

lint:
	@echo "$(BLUE)Running linters...$(NC)"
	@echo "$(YELLOW)Backend:$(NC)"
	@cd backend && ./mvnw checkstyle:check || true
	@echo "$(YELLOW)Frontend:$(NC)"
	@cd frontend && pnpm lint || true

docker-up:
	@echo "$(BLUE)Starting Docker Compose...$(NC)"
	@docker-compose up -d
	@echo "$(GREEN)✓ Services started$(NC)"
	@echo "$(YELLOW)Backend:$(NC) http://localhost:8080"
	@echo "$(YELLOW)Frontend:$(NC) http://localhost:5173"
	@echo "$(YELLOW)PostgreSQL:$(NC) localhost:5432"

docker-down:
	@echo "$(BLUE)Stopping Docker Compose...$(NC)"
	@docker-compose down
	@echo "$(GREEN)✓ Services stopped$(NC)"

docker-logs:
	@docker-compose logs -f

clean:
	@echo "$(BLUE)Cleaning build artifacts...$(NC)"
	@echo "$(YELLOW)Backend:$(NC)"
	@cd backend && ./mvnw clean
	@echo "$(YELLOW)Frontend:$(NC)"
	@cd frontend && rm -rf dist node_modules .eslintcache
	@echo "$(GREEN)✓ Cleanup complete$(NC)"
