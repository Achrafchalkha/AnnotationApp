// Modern UI Interactions
document.addEventListener('DOMContentLoaded', function() {
    // Add animation classes to elements
    const animateElements = document.querySelectorAll('.should-animate');
    animateElements.forEach(element => {
        element.classList.add('animate-fade-in');
    });

    // Initialize dropdowns
    const dropdownButtons = document.querySelectorAll('[data-dropdown-toggle]');
    dropdownButtons.forEach(button => {
        const targetId = button.getAttribute('data-dropdown-toggle');
        const target = document.getElementById(targetId);
        
        if (target) {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                target.classList.toggle('hidden');
            });
            
            // Close when clicking outside
            document.addEventListener('click', (e) => {
                if (!button.contains(e.target) && !target.contains(e.target)) {
                    target.classList.add('hidden');
                }
            });
        }
    });

    // Mobile menu toggle
    const mobileMenuButton = document.getElementById('mobile-menu-button');
    const mobileMenu = document.getElementById('mobile-menu');
    
    if (mobileMenuButton && mobileMenu) {
        mobileMenuButton.addEventListener('click', () => {
            mobileMenu.classList.toggle('hidden');
        });
    }

    // Flash messages auto-dismiss
    const flashMessages = document.querySelectorAll('.flash-message');
    flashMessages.forEach(message => {
        setTimeout(() => {
            message.classList.add('opacity-0');
            setTimeout(() => {
                message.remove();
            }, 300);
        }, 5000);
    });

    // Form validation
    const forms = document.querySelectorAll('form[data-validate]');
    forms.forEach(form => {
        form.addEventListener('submit', (e) => {
            const requiredFields = form.querySelectorAll('[required]');
            let isValid = true;
            
            requiredFields.forEach(field => {
                if (!field.value.trim()) {
                    isValid = false;
                    const errorElement = document.createElement('p');
                    errorElement.classList.add('text-red-500', 'text-sm', 'mt-1');
                    errorElement.textContent = 'This field is required';
                    
                    // Remove any existing error messages
                    const existingError = field.parentNode.querySelector('.text-red-500');
                    if (existingError) {
                        existingError.remove();
                    }
                    
                    field.parentNode.appendChild(errorElement);
                    field.classList.add('border-red-500');
                }
            });
            
            if (!isValid) {
                e.preventDefault();
            }
        });
    });

    // Initialize tooltips
    const tooltipTriggers = document.querySelectorAll('[data-tooltip]');
    tooltipTriggers.forEach(trigger => {
        const tooltipText = trigger.getAttribute('data-tooltip');
        const tooltipElement = document.createElement('div');
        tooltipElement.classList.add('tooltip', 'hidden', 'absolute', 'bg-gray-800', 'text-white', 'text-xs', 'rounded', 'py-1', 'px-2', 'z-50');
        tooltipElement.textContent = tooltipText;
        
        trigger.appendChild(tooltipElement);
        
        trigger.addEventListener('mouseenter', () => {
            tooltipElement.classList.remove('hidden');
        });
        
        trigger.addEventListener('mouseleave', () => {
            tooltipElement.classList.add('hidden');
        });
    });
}); 